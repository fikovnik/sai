package sai.direct.core.parser

trait Expr

case class Var(x: String) extends Expr
case class App(e1: Expr, e2: Expr) extends Expr
case class Lam(x: String, body: Expr) extends Expr

case class Bind(x: String, e: Expr)
case class Let(x: String, e: Expr, body: Expr) extends Expr
case class Lrc(bds: List[Bind], body: Expr) extends Expr

case class Lit(i: Int) extends Expr
case class If0(cnd: Expr, thn: Expr, els: Expr) extends Expr
case class AOp(op: Symbol, e1: Expr, e2: Expr) extends Expr
