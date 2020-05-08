# Visitor scalafix

This repository contains a scalafix rule to generate a `Visitor` trait for ADTs. When it finds a sealed trait or class,
it will:

1. Define an abstract `def visit` declaration to the trait/class
2. Define a `trait Visitor[A]` in the companion object with one method per ADT member
3. Define `def visit` on each ADT member object defined in the companion object, proxying to the correct method in `Visitor`

ADT members defined in nested objects within the companion object will have their object paths reflected in the name of
the corresponding `visit*` method in the `Visitor`.

## Example

For example, this ADT definition:

```scala
sealed trait Test
object Test {
  case object Foo extends Test
  case object Bar extends Test

  object nested {
    case object Baz extends Test
  }
}
```

is transformed into this:

```scala
sealed trait Test {
  def visit[A](v: Test.Visitor[A]): A
}
object Test {
  case object Foo extends Test { def visit[A](v: Test.Visitor[A]): A = v.visitFoo }
  case object Bar extends Test { def visit[A](v: Test.Visitor[A]): A = v.visitBar }

  object nested {
    case object Baz extends Test { def visit[A](v: Test.Visitor[A]): A = v.visitNestedBaz }
  }

  trait Visitor[A] {
    def visitFoo: A
    def visitBar: A
    def visitNestedBaz: A
  }
}
```

## Caveats

- Formatting of source files is not preserved, so this rule is best combined with scalafmt
- The rule only discovers and modifies ADT members defined in the sealed trait/class' companion object
  ```scala
  sealed trait Foo
  object Foo {
    case object Bar extends Foo // discovered
  }
  case object Baz extends Foo // not discovered
  ```
- The rule only recurses into nested objects within the companion object, not other classes or traits
  ```scala
  sealed trait Foo
  object Foo {
    object NestedObject {
      case object Bar extends Foo // discovered
    }
    class NestedClass {
      case object Baz extends Foo // not discovered
    }
  }
  ```
- The rule will not modify or remove unrelated statements defined in the sealed trait/class, the companion object, any of the nested objects, or in any of the ADT members
  ```scala
  sealed trait Foo {
    def other(): Unit // preserved
  }
  object Foo {
    def dontTouchMe(): Unit // preserved

    case object Bar extends Foo {
      def other(): Unit = () // preserved
    }
  }
  ```
- The rule will overwrite any code that conflicts with the naming/type conventions it sets, namely:
  - If `def visit` is already declared in the trait/class but is defined with a different type or parameter name
  - If `def visit` is already defined in an ADT member but is defined with a different type, parameter name, or with a call to a method on the `Visitor` not following the set naming convention
  - If `trait Visitor[A]` is defined in any way differently than having one abstract `def` per ADT member following the set naming convention
