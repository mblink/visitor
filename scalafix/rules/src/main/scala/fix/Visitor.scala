package fix

import scalafix.v1._
import scala.meta._
import scala.meta.contrib._
import scala.meta.contrib.equality.Structurally

class Visitor extends SyntacticRule("Visitor") {
  override def fix(implicit doc: SyntacticDocument): Patch =
    findAdtTpes.map(tpe => tpe.patch + tpe.patchCompanion).asPatch

  private def findAdtTpes(implicit doc: SyntacticDocument): List[AdtTpe] = {
    def go(scope: List[Tree], tree: Tree): List[AdtTpe] =
      tree match {
        case t: Defn.Trait if t.mods.has[Mod.Sealed] => List(AdtTpe(Left(t), scope))
        case c: Defn.Class if c.mods.has[Mod.Sealed] => List(AdtTpe(Right(c), scope))
        case t => t.children.flatMap(go(t.children, _))
      }

    go(Nil, doc.tree)
  }

  private def upsertStat[T: Extract[?, Stat]: Replace[?, Stat], A <: Stat](tree: T)(stat: A)(pf: PartialFunction[Stat, A]): T = {
    val (wasReplaced, updStats) = tree.extract[Stat].foldRight((false, List[Stat]())) { case (t, (replaced, stats)) =>
      pf.lift(t) match {
        case Some(_) => (true, stat :: stats)
        case None => (replaced, t :: stats)
      }
    }

    tree.withStats(if (wasReplaced) updStats else updStats :+ stat)
  }

  case class AdtTpe(run: Either[Defn.Trait, Defn.Class], siblings: List[Tree]) { self =>
    lazy val tree: Tree = run.merge

    lazy val tpeName: Type.Name = run.fold(_.name, _.name)

    lazy val companion: Option[Defn.Object] =
      siblings.collectFirst { case o @ Defn.Object(_, Term.Name(n), _) if n == tpeName.value => o }

    lazy val visitorTpe: Type = Type.Apply(
      Type.Select(Term.Name(tpeName.value), Type.Name("Visitor")),
      List(Type.Name("A")))

    def mkVisitDef[D](f: Decl.Def => D): D =
      f(q"def visit[A](visitor: $visitorTpe): A")

    lazy val visitDef = mkVisitDef(identity)

    lazy val template: Template = run.fold(_.templ, _.templ)

    lazy val updatedTemplate: Template =
      upsertStat(template)(visitDef) {
        case d @ Decl.Def(_, Term.Name("visit"), _, _, _) => d
      }

    lazy val updatedTree: Tree =
      run.fold(_.copy(templ = updatedTemplate), _.copy(templ = updatedTemplate))

    lazy val patch: Patch =
      Patch.replaceTree(tree, updatedTree.syntax)

    private def handleObject(obj: Defn.Object, parents: List[Defn.Object]): (Defn.Object, List[AdtMember]) = {
      val (updStats, members) = obj.templ.stats.foldRight((List[Stat](), List[AdtMember]())) { case (s, (ss, ms)) =>
        val (upd, foundMs) = findMembers(s, parents :+ obj)
        (upd :: ss, foundMs ++ ms)
      }
      (obj.copy(templ = obj.templ.copy(stats = updStats)), members)
    }

    private def findMembers[T <: Tree](tree: T, parents: List[Defn.Object]): (T, List[AdtMember]) =
      tree match {
        case o: Defn.Object if o.templ.inits.exists(i => Structurally.equal(i.tpe, tpeName)) =>
          val member = AdtMember(self, o, parents)
          val (updObj, nestedMembers) = handleObject(member.updatedTree, parents)
          (updObj, member :: nestedMembers).asInstanceOf[(T, List[AdtMember])]

        case o: Defn.Object =>
          handleObject(o, parents).asInstanceOf[(T, List[AdtMember])]

        case t => (t, Nil)
      }

    def visitorTrait(members: List[AdtMember]): Defn.Trait = q"""
      trait Visitor[A] {
        ..${members.map(m => q"def ${m.visitName}: A")}
      }
    """

    lazy val patchCompanion: Patch = companion match {
      case Some(obj) =>
        val (updObj, members) = findMembers(obj, Nil)
        Patch.replaceTree(obj, upsertStat(updObj)(visitorTrait(members)) {
          case t @ Defn.Trait(_, Type.Name("Visitor"), _, _, _) => t
        }.syntax)

      // no companion means no ADT members
      case None => Patch.empty
    }
  }

  case class AdtMember(adt: AdtTpe, obj: Defn.Object, parents0: List[Defn.Object]) {
    lazy val parents: List[Defn.Object] =
      adt.companion.fold(parents0)(c => parents0.filterNot(_ == c))

    lazy val visitName: Term.Name =
      Term.Name((parents :+ obj).foldLeft("visit")((acc, o) => s"${acc}${o.name.value.capitalize}"))

    lazy val visitDef: Defn.Def =
      adt.mkVisitDef(_ match {
        case q"def visit[A](visitor: $vTpe): A" =>
          q"def visit[A](visitor: $vTpe): A = visitor.$visitName"
      })

    lazy val updatedTree: Defn.Object =
      obj.copy(templ = upsertStat(obj.templ)(visitDef) {
        case d @ Defn.Def(_, Term.Name("visit"), _, _, _, _) => d
      })
  }
}
