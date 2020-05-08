package fix

sealed trait Test { def visit[A](visitor: Test.Visitor[A]): A }

object Test {
  case object Foo extends Test { def visit[A](visitor: Test.Visitor[A]): A = visitor.visitFoo }
  case object Bar extends Test { def visit[A](visitor: Test.Visitor[A]): A = visitor.visitBar }
  def preserved(): Unit = ()
  object Thingy
  object nested {
    case object Baz extends Test { def visit[A](visitor: Test.Visitor[A]): A = visitor.visitNestedBaz }
    class Ignored {
      def whatever(): Unit = ()
      case object NotFound extends Test { def visit[A](visitor: Test.Visitor[A]): A = ??? }
    }
    object other {
      case object Quux extends Test { def visit[A](visitor: Test.Visitor[A]): A = visitor.visitNestedOtherQuux }
      object reallyNested { case object PoorlyNamed extends Test { def visit[A](visitor: Test.Visitor[A]): A = visitor.visitNestedOtherReallyNestedPoorlyNamed } }
    }
  }
  trait Visitor[A] {
    def visitFoo: A
    def visitBar: A
    def visitNestedBaz: A
    def visitNestedOtherQuux: A
    def visitNestedOtherReallyNestedPoorlyNamed: A
  }
}
