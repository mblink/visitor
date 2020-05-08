/*
rule = Visitor
*/
package fix

sealed trait Test

object Test {
  // root level objects get updated
  case object Foo extends Test
  case object Bar extends Test

  // unrelated statements in companion object are ignored
  def preserved(): Unit = ()
  object Thingy

  object nested {
    // nested objects get updated
    case object Baz extends Test

    // unrelated statements in nested objects are ignored
    class Ignored {
      def whatever(): Unit = ()

      // ADT members defined in traits/classes are ignored
      case object NotFound extends Test { def visit[A](visitor: Test.Visitor[A]): A = ??? }
    }

    object other {
      // deeply nested objects get updated
      case object Quux extends Test

      object reallyNested {
        case object PoorlyNamed extends Test {
          // `visit` definitions not following the naming convention are rewritten
          def visit[A](visitor: Test.Visitor[A]): A = visitor.poorlyNamed
        }
      }
    }
  }

  // existing trait doesn't get duplicated
  trait Visitor[A] {
    // methods not following the naming convention are rewritten
    def poorlyNamed: A

    // irrelevant methods in the trait are removed
    def removed(): Unit = ()
  }
}
