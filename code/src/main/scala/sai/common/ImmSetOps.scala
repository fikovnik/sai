package sai.common

import scala.lms.common._
import scala.lms.tutorial._
import scala.reflect.SourceContext
import scala.lms.internal.GenericNestedCodegen

/* LMS support for immutable Set. */

trait SetOps extends Base with Variables {
  implicit def setTyp[T:Typ]: Typ[Set[T]]

  object Set {
    def apply[A:Typ](xs: Rep[A]*)(implicit pos: SourceContext) = set_new[A](xs)
  }

  implicit def repSetToSetOps[A:Typ](v: Rep[Set[A]]) = new SetOpsCls(v)

  class SetOpsCls[A:Typ](s: Rep[Set[A]]) {
    def ++(s1: Rep[Set[A]])(implicit pos: SourceContext) = set_concat(s, s1)
    def isEmpty()(implicit pos: SourceContext) = set_isEmpty(s)
    def head()(implicit pos: SourceContext) = set_head(s)
    def tail()(implicit pos: SourceContext) = set_tail(s)
    def intersect(s1: Rep[Set[A]])(implicit pos: SourceContext) = set_intersect(s, s1)
    def union(s1: Rep[Set[A]])(implicit pos: SourceContext) = set_union(s, s1)
    def subsetOf(s1: Rep[Set[A]])(implicit pos: SourceContext) = set_subsetof(s, s1)
    def foldLeft[B:Typ](z: Rep[B])(f: Rep[((B, A)) => B])(implicit pos: SourceContext) = set_foldLeft(s, z, f)
  }

  def set_new[A:Typ](xs: Seq[Rep[A]])(implicit pos: SourceContext): Rep[Set[A]]

  def set_concat[A:Typ](s1: Rep[Set[A]], s2: Rep[Set[A]])(implicit pos: SourceContext): Rep[Set[A]]

  def set_isEmpty[A:Typ](s: Rep[Set[A]])(implicit pos: SourceContext): Rep[Boolean]

  def set_head[A:Typ](s: Rep[Set[A]])(implicit pos: SourceContext): Rep[A]

  def set_tail[A:Typ](s: Rep[Set[A]])(implicit pos: SourceContext): Rep[Set[A]]

  def set_intersect[A:Typ](s1: Rep[Set[A]], s2: Rep[Set[A]])(implicit pos: SourceContext): Rep[Set[A]]

  def set_union[A:Typ](s1: Rep[Set[A]], s2: Rep[Set[A]])(implicit pos: SourceContext): Rep[Set[A]]

  def set_subsetof[A:Typ](s1: Rep[Set[A]], s2: Rep[Set[A]])(implicit pos: SourceContext): Rep[Boolean]

  def set_foldLeft[A:Typ,B:Typ](s: Rep[Set[A]], z: Rep[B], f: Rep[((B, A)) => B])(implicit pos: SourceContext): Rep[B]
}

trait SetOpsExp extends SetOps with EffectExp with VariablesExp with BooleanOpsExp with TupledFunctionsExp {
  implicit def setTyp[T:Typ]: Typ[Set[T]] = {
    implicit val ManifestTyp(m) = typ[T]
    manifestTyp
  }

  case class SetNew[A:Typ](xs: Seq[Exp[A]], mA: Typ[A]) extends Def[Set[A]]

  case class SetConcat[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]]) extends Def[Set[A]]

  case class SetIsEmpty[A:Typ](s: Exp[Set[A]]) extends Def[Boolean]

  case class SetHead[A:Typ](s: Exp[Set[A]]) extends Def[A]

  case class SetTail[A:Typ](s: Exp[Set[A]]) extends Def[Set[A]]

  case class SetIntersect[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]]) extends Def[Set[A]]

  case class SetUnion[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]]) extends Def[Set[A]]

  case class SetSubsetOf[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]]) extends Def[Boolean]

  case class SetFoldLeft[A:Typ,B:Typ](s: Exp[Set[A]], z: Exp[B], f: Exp[((B, A)) => B]) extends Def[B]

  def set_new[A:Typ](xs: Seq[Exp[A]])(implicit pos: SourceContext) = SetNew(xs, typ[A])

  def set_concat[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]])(implicit pos: SourceContext) = SetConcat(s1, s2)

  def set_isEmpty[A:Typ](s: Exp[Set[A]])(implicit pos: SourceContext) = SetIsEmpty(s)

  def set_head[A:Typ](s: Exp[Set[A]])(implicit pos: SourceContext) = SetHead(s)

  def set_tail[A:Typ](s: Exp[Set[A]])(implicit pos: SourceContext) = SetTail(s)
  
  def set_intersect[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]])(implicit pos: SourceContext) = SetIntersect(s1, s2)

  def set_union[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]])(implicit pos: SourceContext) = SetUnion(s1, s2)

  def set_subsetof[A:Typ](s1: Exp[Set[A]], s2: Exp[Set[A]])(implicit pos: SourceContext) = SetSubsetOf(s1, s2)

  def set_foldLeft[A:Typ,B:Typ](s: Exp[Set[A]], z: Exp[B], f: Exp[((B, A)) => B])(implicit pos: SourceContext) =
    SetFoldLeft(s, z, f)
}

trait BaseGenSetOps extends GenericNestedCodegen {
  val IR: SetOpsExp
  import IR._
}

trait ScalaGenSetOps extends BaseGenSetOps with ScalaGenEffect {
  val IR: SetOpsExp
  import IR._

  override def emitNode(sym: Sym[Any], rhs: Def[Any]) = rhs match {
    case SetNew(xs, mA) => emitValDef(sym, src"collection.immutable.Set[$mA](" + (xs map {quote}).mkString(",") + ")")
    case SetConcat(s1, s2) => emitValDef(sym, src"$s1 ++ $s2")
    case SetIsEmpty(s) => emitValDef(sym, src"$s.isEmpty")
    case SetHead(s) => emitValDef(sym, src"$s.head")
    case SetTail(s) => emitValDef(sym, src"$s.tail")
    case SetIntersect(s1, s2) => emitValDef(sym, src"$s1.intersect($s2)")
    case SetUnion(s1, s2) => emitValDef(sym, src"$s1.union($s2)")
    case SetSubsetOf(s1, s2) => emitValDef(sym, src"$s1.subsetOf($s2)")
    case SetFoldLeft(s, z, f) => emitValDef(sym, src"$s.foldLeft($z)($f)")
    case _ => super.emitNode(sym, rhs)
  }
}
