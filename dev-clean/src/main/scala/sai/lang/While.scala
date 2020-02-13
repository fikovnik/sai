package sai.lang

import lms.core._
import lms.core.stub._
import lms.core.Backend._
import lms.core.virtualize
import lms.macros.SourceContext

import sai.lmsx._

import scala.collection.immutable.{List => SList}

object WhileLang {
  sealed trait Stmt
  case class Skip() extends Stmt
  case class Assign(x: String, e: Expr) extends Stmt
  case class Cond(e: Expr, thn: Stmt, els: Stmt) extends Stmt
  case class Seq(s1: Stmt, s2: Stmt) extends Stmt
  case class While(b: Expr, s: Stmt) extends Stmt

  sealed trait Expr
  case class Lit(x: Any) extends Expr
  case class Var(x: String) extends Expr
  case class Op1(op: String, e: Expr) extends Expr
  case class Op2(op: String, e1: Expr, e2: Expr) extends Expr

  object Examples {
    val fact5 =
      Seq(Assign("i", Lit(1)),
          Seq(Assign("fact", Lit(1)),
              While(Op2("<=", Var("i"), Lit(5)),
                    Seq(Assign("fact", Op2("*", Var("fact"), Var("i"))),
                      Assign("i", Op2("+", Var("i"), Lit(1)))))))

    val cond1 =
      Cond(Op2("<=", Lit(1), Lit(2)),
        Assign("x", Lit(3)),
        Assign("x", Lit(4)))

    val cond2 =
      Seq(Cond(Op2("<=", Var("x"), Var("y")),
               Assign("z", Var("x")),
               Assign("z", Var("y"))),
          Assign("z", Op2("+", Var("z"), Lit(1))))

  }
}

import sai.structure.monad._

object WhileSemantics {
  import WhileLang._
  import CpsM._

  trait Value
  case class IntV(i: Int) extends Value
  case class BoolV(b: Boolean) extends Value

  type Ans = Value
  type Store = Map[String, Value]
  type M[T] = CpsM[Ans, T]

  val M = Monad[M]
  import M._

  def eval(e: Expr, σ: Store): Value = e match {
    case Lit(i: Int) => IntV(i)
    case Lit(b: Boolean) => BoolV(b)
    case Var(x) => σ(x)
    case Op1("-", e) =>
      val IntV(i) = eval(e, σ)
      IntV(-i)
    case Op2(op, e1, e2) =>
      val IntV(i1) = eval(e1, σ)
      val IntV(i2) = eval(e2, σ)
      op match {
        case "+" => IntV(i1 + i2)
        case "-" => IntV(i1 - i2)
        case "*" => IntV(i1 * i2)
        case "==" => BoolV(i1 == i2)
        case "<=" => BoolV(i1 <= i2)
        case "<" => BoolV(i1 < i2)
        case ">=" => BoolV(i1 >= i2)
        case ">" => BoolV(i1 > i2)
      }
  }

  def fix[A, B](f: (A => B) => A => B): A => B = a => f(fix(f))(a)

  def exec(s: Stmt)(σ: Store): M[Store] = s match {
    case Skip() => pure(σ)
    case Assign(x, e) => pure(σ + (x → eval(e, σ)))
    case Cond(e, s1, s2) =>
      val BoolV(b) = eval(e, σ)
      if (b) exec(s1)(σ) else exec(s2)(σ)
    case Seq(s1, s2) =>
      exec(s1)(σ).flatMap(σ => exec(s2)(σ))
    case While(e, s) => fix((f: Store => M[Store]) => (σ: Store) => {
                              CpsM((k: Store => Ans) => {
                                     val BoolV(b) = eval(e, σ)
                                     if (b) exec(s)(σ)(σ1 => f(σ1)(k)) else k(σ)
                                   })
                            })(σ)
  }
}

@virtualize
trait StagedWhileSemantics extends SAIOps {
  import WhileLang._
  //import CpsM._
  import StateT._

  type Ans = Store
  type Store = Map[String, Value]
  //type M[T] = CpsM[Ans, T]
  type M[T] = StateT[IdM, Ans, T]
  val M = Monad[M]
  import M._

  trait Value
  def IntV(i: Rep[Int]): Rep[Value] =
    Wrap[Value](Adapter.g.reflect("IntV", Unwrap(i)))
  def BoolV(b: Rep[Boolean]): Rep[Value] =
    Wrap[Value](Adapter.g.reflect("BoolV", Unwrap(b)))

  def rep_int_proj(i: Rep[Value]): Rep[Int] = Unwrap(i) match {
    case Adapter.g.Def("IntV", scala.collection.immutable.List(v: Backend.Exp)) =>
      Wrap[Int](v)
    case _ =>
      Wrap[Int](Adapter.g.reflect("IntV-proj", Unwrap(i)))
  }

  def rep_bool_proj(b: Rep[Value]): Rep[Boolean] = Unwrap(b) match {
    case Adapter.g.Def("BoolV", scala.collection.immutable.List(v: Backend.Exp)) =>
      Wrap[Boolean](v)
    case _ =>
      Wrap[Boolean](Adapter.g.reflect("BoolV-proj", Unwrap(b)))
  }

  def eval(e: Expr, σ: Rep[Store]): Rep[Value] = e match {
    case Lit(i: Int) => IntV(i)
    case Lit(b: Boolean) => BoolV(b)
    case Var(x) => σ(x)
    case Op1("-", e) =>
      val i = rep_int_proj(eval(e, σ))
      IntV(-i)
    case Op2(op, e1, e2) =>
      val i1 = rep_int_proj(eval(e1, σ))
      val i2 = rep_int_proj(eval(e2, σ))
      op match {
        case "+" => IntV(i1 + i2)
        case "-" => IntV(i1 - i2)
        case "*" => IntV(i1 * i2)
        case "==" => BoolV(i1 == i2)
        case "<=" => BoolV(i1 <= i2)
        case "<" => BoolV(i1 < i2)
        case ">=" => BoolV(i1 >= i2)
        case ">" => BoolV(i1 > i2)
      }
  }

  def fix[A: Manifest, B: Manifest](f: Rep[A => B] => Rep[A => B]): Rep[A => B] = {
    def g: Rep[A => B] = fun({ case (a: Rep[A]) => f(g)(a) })
    g
  }

  def power(x: Rep[Int])(f: Rep[Int => Int]): Rep[Int => Int] = fun({ (n: Rep[Int]) =>
    if (n == 0) 1
    else x * f(n - 1)
  })

  def power3: Rep[Int => Int] = fix(power(3))

  def get_state: M[Store] = MonadState[M, Store].get
  def update_state(x: String, v: Rep[Value]): M[Unit] =
    MonadState[M, Store].mod(s => s + (unit(x) -> v))
  def lift_state(s: Rep[Store]): M[Unit] =
    MonadState[M, Store].put(s)

  def evalM(e: Expr): M[Value] = for {
    σ <- get_state
  } yield eval(e, σ)

  def exec(s: Stmt): M[Unit] = s match {
    case Skip() => pure(())
    case Assign(x, e) => for {
      v <- evalM(e)
      _ <- update_state(x, v)
    } yield ()
    case Cond(e, s1, s2) => for {
      cnd <- evalM(e)
      σ <- get_state
      rt <- lift_state(if (rep_bool_proj(cnd)) exec(s1)(σ).run._2 else exec(s2)(σ).run._2)
    } yield ()
    case Seq(s1, s2) => for {
      _ <- exec(s1); _ <- exec(s2)
    } yield ()
    case While(e, b) =>
      def f: Rep[Store => Store] =
        fun({ s =>
              val ans = for {
                cnd <- evalM(e)
                σ <- get_state
                rt <- lift_state(if (rep_bool_proj(cnd)) f(exec(b)(σ).run._2) else σ)
              } yield ()
              ans(s).run._2
            })
      for {
        σ <- get_state
        σ1 <- lift_state(f(σ))
      } yield ()
  }

  /* 
  //The CPS Monad doesn't work for the While case
  def fix[A, B](f: (Rep[A] => B) => Rep[A] => B): Rep[A] => B = { a =>
    f(fix(f))(a)
  }

  def exec(s: Stmt)(σ: Rep[Store]): M[Store] = s match {
    case Skip() => pure(σ)
    case Assign(x, e) => pure(σ + (x → eval(e, σ)))
    case Cond(e, s1, s2) =>
      val b = rep_bool_proj(eval(e, σ))
      pure(if (b) exec(s1)(σ)(s => s) else exec(s2)(σ)(s => s))
    case Seq(s1, s2) =>
      exec(s1)(σ).flatMap(σ => exec(s2)(σ))
    case While(e, s) =>
      fix((f: Rep[Store] => M[Store]) => (σ: Rep[Store]) => {
        CpsM((k: Rep[Store] => Rep[Ans]) => {
          val b = rep_bool_proj(eval(e, σ))
          if (b) exec(s)(σ)(σ1 => f(σ1)(k)) else k(σ)
        })
      })(σ)
  }
   */
}

@virtualize
trait SymStagedWhile extends SAIOps {
  import WhileLang._
  import StateT._
  import ListT._

  trait PC

  trait Value
  def IntV(i: Rep[Int]): Rep[Value] =
    Wrap[Value](Adapter.g.reflect("IntV", Unwrap(i)))
  def BoolV(b: Rep[Boolean]): Rep[Value] =
    Wrap[Value](Adapter.g.reflect("BoolV", Unwrap(b)))
  def SymV(x: Rep[String]): Rep[Value] =
    Wrap[Value](Adapter.g.reflect("SymV", Unwrap(x)))

  def rep_int_proj(i: Rep[Value]): Rep[Int] = Unwrap(i) match {
    case Adapter.g.Def("IntV", scala.collection.immutable.List(v: Backend.Exp)) =>
      Wrap[Int](v)
    case _ =>
      Wrap[Int](Adapter.g.reflect("IntV-proj", Unwrap(i)))
  }

  def rep_bool_proj(b: Rep[Value]): Rep[Boolean] = Unwrap(b) match {
    case Adapter.g.Def("BoolV", scala.collection.immutable.List(v: Backend.Exp)) =>
      Wrap[Boolean](v)
    case _ =>
      Wrap[Boolean](Adapter.g.reflect("BoolV-proj", Unwrap(b)))
  }

  type Store = Map[String, Value]
  type Ans = (Store, Set[PC])
  type M[T] = StateT[ListT[IdM, ?], Ans, T]

  def eval(e: Expr, σ: Rep[Store]): Rep[Value] = e match {
    case Lit(i: Int) => IntV(i)
    case Lit(b: Boolean) => BoolV(b)
    case Var(x) => σ(x)
    case Op1("-", e) =>
      val v = eval(e, σ)
      Unwrap(v) match {
        case Adapter.g.Def("IntV", scala.collection.immutable.List(v: Backend.Exp)) =>
          val v1: Rep[Int] = Wrap[Int](v)
          IntV(-v1)
        case Adapter.g.Def("SymV", scala.collection.immutable.List(v: Backend.Exp)) =>
          val v1: Rep[String] = Wrap[String](v)
          SymV(unit("-" + v1))
        case i =>
          val v1: Rep[Int] = Wrap[Int](Adapter.g.reflect("IntV-proj", i))
          IntV(-v1)
      }
    case Op2(op, e1, e2) =>
      val i1 = eval(e1, σ)
      val i2 = eval(e2, σ)
      Wrap[Value](Adapter.g.reflect("op", Unwrap(unit(op)), Unwrap(i1), Unwrap(i2)))
  }

  def get_state: M[Store] = ???
  def update_state(x: String, v: Rep[Value]): M[Unit] = ???
  def lift_state(s: Rep[Store]): M[Unit] = ???

  def evalM(e: Expr): M[Value] = for {
    σ <- get_state
  } yield eval(e, σ)
}


trait StagedWhileGen extends SAICodeGenBase {
  override def shallow(n: Node): Unit = n match {
    case Node(s, "IntV", List(i), _) =>
      emit("IntV(")
      shallow(i)
      emit(")")
    case Node(s, "IntV-proj", List(i), _) =>
      shallow(i)
      emit(".asInstanceOf[IntV].i")
    case Node(s, "BoolV", List(b), _) =>
      emit("BoolV(")
      shallow(b)
      emit(")")
    case Node(s, "BoolV-proj", List(i), _) =>
      shallow(i)
      emit(".asInstanceOf[BoolV].b")
    case _ => super.shallow(n)
  }
}

trait StagedWhileDriver extends SAIDriver[Unit, Unit] with StagedWhileSemantics { q =>
  override val codegen = new ScalaGenBase with StagedWhileGen {
    val IR: q.type = q
    import IR._
    override def remap(m: Manifest[_]): String = {
      if (m.toString.endsWith("$Value")) "Value"
      else super.remap(m)
    }
  }

  override val prelude =
"""
import sai.lang.WhileLang._
trait Value
case class IntV(i: Int) extends Value
case class BoolV(b: Boolean) extends Value
"""
}

object TestWhile {
  import WhileLang._
  import WhileLang.Examples._

  @virtualize
  def specialize(e: Expr): SAIDriver[Unit, Unit] = new StagedWhileDriver {
    def snippet(u: Rep[Unit]) = {
      val v = eval(e, Map())
      println(v)
    }
  }

  @virtualize
  def specialize(s: Stmt): SAIDriver[Unit, Unit] = new StagedWhileDriver {
    def snippet(u: Rep[Unit]) = {
      val v = exec(s)(Map("x" -> IntV(3), "z" -> IntV(4))).run
      println(v)
      /*
      val x = power3(2)
      println(x)
      val y = power3(3)
      println(y)
       */
    }
  }

  def main(args: Array[String]): Unit = {
    //println(exec(fact5)(Map())(σ => σ("fact")))
    //val code = specialize(Op2("+", Lit(1), Lit(2)))
    val code = specialize(fact5)
    println(code.code)
    code.eval(())
  }
}
