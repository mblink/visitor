package fix

sealed trait Test { def visit[A](visitor: Test.Visitor[A]): A }

object Test {
  case object Foo extends Test {
    def visit[A](visitor: Test.Visitor[A]): A = visitor.visitFoo
  }
  case object Bar extends Test {
    def visit[A](visitor: Test.Visitor[A]): A = visitor.visitBar
  }

  object nested {
    case object Baz extends Test {
      def visit[A](visitor: Test.Visitor[A]): A = visitor.visitNestedBaz
    }

    object other {
      case object Quux extends Test {
        def visit[A](visitor: Test.Visitor[A]): A = visitor.visitNestedOtherQuux
      }
    }
  }

  trait Visitor[A] {
    def visitFoo: A
    def visitBar: A
    def visitNestedBaz: A
    def visitNestedOtherQuux: A
  }
}
