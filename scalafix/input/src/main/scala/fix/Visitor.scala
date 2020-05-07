/*
rule = Visitor
*/
package fix

sealed trait Test

object Test {
  case object Foo extends Test
  case object Bar extends Test

  object nested {
    case object Baz extends Test

    object other {
      case object Quux extends Test
    }
  }
}
