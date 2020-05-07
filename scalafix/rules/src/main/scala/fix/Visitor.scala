package fix

import scalafix.v1._
import scala.meta._

class Visitor extends SyntacticRule("Visitor") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val sealedTpes: List[Either[Defn.Trait, Defn.Class]] = doc.tree.collect {
      case t: Defn.Trait if t.mods.has[Mod.Sealed] => Left(t)
      case c: Defn.Class if c.mods.has[Mod.Sealed] => Right(c)
    }
    sealedTpes.map(updateSealedTpe).asPatch
  }

  private def visitorType(tpe: Either[Defn.Trait, Defn.Class]): Type =
    Type.Apply(
      Type.Select(Term.Name(tpe.fold(_.name, _.name).value), Type.Name("Visitor")),
      List(Type.Name("A")))

  private def updateSealedTpe(tpe: Either[Defn.Trait, Defn.Class]): Patch = {
    val abstractDef = q"def visit[A](visitor: ${visitorType(tpe)}): A"
    tpe.fold(
      t => Patch.replaceTree(t, t.copy(templ = t.templ.copy(stats = t.templ.stats :+ abstractDef)).syntax),
      c => Patch.replaceTree(c, c.copy(templ = c.templ.copy(stats = c.templ.stats :+ abstractDef)).syntax))
  }
}
