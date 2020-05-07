package fix

import scalafix.v1._
import scala.meta._

class Visitor extends SyntacticRule("Visitor") {
  override def fix(implicit doc: SyntacticDocument): Patch = {
    val sealedTpes: List[Either[Defn.Trait, Defn.Class]] = doc.tree.collect {
      case t: Defn.Trait if t.mods.has[Mod.Sealed] => Left(t)
      case c: Defn.Class if c.mods.has[Mod.Sealed] => Right(c)
    }
    val members: List[(Either[Defn.Trait, Defn.Class], List[Defn.Object])] = sealedTpes.map(t => t -> findMembers(t))
    (sealedTpes.map(updateSealedTpe) ++ members.flatMap { case (tpe, objs) => objs.map(updateMember(tpe, _)) }).asPatch
  }

  private def tpeName(tpe: Either[Defn.Trait, Defn.Class]): Type.Name =
    tpe.fold(_.name, _.name)

  private def visitorType(tpe: Either[Defn.Trait, Defn.Class]): Type =
    Type.Apply(
      Type.Select(Term.Name(tpeName(tpe).value), Type.Name("Visitor")),
      List(Type.Name("A")))

  private def visitDef[D](tpe: Either[Defn.Trait, Defn.Class], f: Decl.Def => D): D =
    f(q"def visit[A](visitor: ${visitorType(tpe)}): A")

  private def updateSealedTpe(tpe: Either[Defn.Trait, Defn.Class]): Patch = {
    val (tree, tpl, setTpl) = (tpe.merge, tpe.fold(_.templ, _.templ), (tp: Template) => tpe.fold(_.copy(templ = tp), _.copy(templ = tp)))
    Patch.replaceTree(tree, setTpl(tpl.copy(stats = tpl.stats :+ visitDef(tpe, identity))).syntax)
  }

  private def findMembers(tpe: Either[Defn.Trait, Defn.Class])(implicit doc: SyntacticDocument): List[Defn.Object] =
    doc.tree.collect {
      case o: Defn.Object if o.templ.inits.map(_.tpe).collectFirst { case t if t == tpeName(tpe) => t }.nonEmpty => o
    }

  private def updateMember(tpe: Either[Defn.Trait, Defn.Class], member: Defn.Object): Patch = {
    println(s"$tpe $member")
    Patch.empty
  }
}
