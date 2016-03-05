package typeclassic

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros.whitebox
import macrocompat.bundle

/**
 * Typeclassic's ops macros are a generalization of machinist.
 *
 * The goal is to be able to rewrite macro applications into some
 * method call on a combination of arguments. We need to be able to
 * "change" which object's method is invoked, the name of the method,
 * and the order of the arguments.
 *
 * @op syntax:
 *
 * Paramters consist of a $ followed by one-or-more lowercase letters
 * (a-z). These parameter names will correspond to the parameter trees
 * matched by the parseApplication() method. An @op prototype MUST
 * mention every tree -- if there are 3 trees, there must be 3
 * parameter names. Unused parameter names can be specified via a
 * trailing {} stanza.
 *
 * Examples:
 *
 * $y.compare($x, $z)
 *   In this example the type class is $y, the second parameter tree.
 *   It's like that something like ops(a)(ev).compare(b) was matched.
 *
 * $y {$x}
 *   In this example we are throwing away the first parameter and just
 *   returning the second. It's likely we matched Companion.method(ev)
 *   and just want to get the ev value directly.
 *
 * $y.negate($x)
 *  In this example we likely matched ops(a)(ev).unary_-().
 *
 * You could imagine all kinds of detailed rewrites with many function
 * applications. Currently a prototype supports at-most one function
 * application (the starting parameter followed by a dot and a name)
 * but this is just a limitation of the current parser.
 *
 * One nice thing is that we should be able to use this to "inline"
 * certain simple implementations, for example you could imagine
 * something like:
 *
 * implicit class LitOps(n: Int) {
 *   @op("Real($x) * $y")
 *   def *(x1: Real): Real = Ops.macros10
 * }
 *
 * This handles a huge case of implicit operators which machinist is
 * not currently able to deal with (machinist requires a type class
 * instance to rewrite to, rather than this more generate prototype
 * strategy).
 */
@bundle
class OpsMacros(val c: whitebox.Context) {
  import c.universe._

  // abort compilation with a hopefully helpful message
  def die(s: String): Nothing =
    c.abort(c.enclosingPosition, s)

  // determine if a given annotation is @op or not.
  def isOp(a: Annotation): Boolean =
    a.tree.tpe =:= typeOf[op]

  // look for an @op containing the prototype we need for our macros
  def getProto(name: TermName): Option[String] =
    c.prefix.tree.tpe.decl(name).asMethod.annotations.filter(isOp) match {
      case List(ann) =>
        ann.tree.children.tail match {
          case List(Literal(Constant(proto))) =>
            Some(proto.asInstanceOf[String])
          case _ => None
        }
      case _ => None
    }

  // we will definitely need to add more shapes here as more cases go
  // up. the key is that we need to add all values that might possibly
  // be arguments (or have methods invoked on them) in the order that
  // they appeared.
  def parseApplication(t: Tree): (List[Tree], TermName) = {
    t match {
      case Select(Apply(Apply(TypeApply(_, _), cs), ds), TermName(name)) =>
        (cs ::: ds, TermName(name))
      case Apply(Select(Apply(Apply(TypeApply(_, _), cs), ds), TermName(name)), es) =>
        (cs ::: ds ::: es, TermName(name))
      case Apply(TypeApply(Select(c, TermName(name)), _), ds) =>
        (c :: ds, TermName(name))
      case t =>
        die(s"cannot parse application shape: ${showRaw(t)}")
    }
  }

  // parse the macro application, and look for a prototype
  def parse(t: Tree): (List[Tree], String) = {
    val (trees, term) = parseApplication(t)
    getProto(term) match {
      case Some(proto) => (trees, proto)
      case _ => die(s"could not find an @op annotation: $t")
    }
  }

  // useful regular expressions
  val V = """(\$[a-z]+)""".r
  val P1 = """^(\$[a-z]+)\.([^()]+)(.+)$""".r
  val P2 = """^\(([^()]+)\)$""".r
  val I = """^(.+) \{[^}]*\}$""".r

  // this lexer is currently way too brittle but works for the given
  // cases. it definitely should be made more rigorous.
  def lexer(s: String, table: Map[String, Tree]): Tree =
    s match {
      case I(str) =>
        lexer(str, table)
      case V(name) =>
        table(name)
      case P1(obj, meth, rest) =>
        val toks = P2.findAllMatchIn(rest).map(_.group(1)).toList
        val stanzas = toks.map(_.split(", ").toList)
        (obj, meth, stanzas)
        val t0: Tree = Select(table(obj), TermName(meth))
        stanzas.foldLeft(t0)((t, stanza) => Apply(t, stanza.map(table(_))))
    }

  // this is the top-level macro method that is called. it
  // auto-detects everything it needs to create a new tree (the
  // argument trees to the macro application and the prototype), and
  // then constructs a new tree.
  //
  // we let scalac re-typecheck the tree, so at this moment we don't
  // have to worry if the types line up or not.
  def interpret(): Tree = {
    val t = c.macroApplication
    val (trees, proto) = parse(t)
    val names = V.findAllMatchIn(proto).map(_.matched).toList.sorted
    val table = (names zip trees).toMap
    val tree = lexer(proto, table)
    println(t)
    println(s"    becomes $tree")
    println("")
    tree
  }

  // an ugly thing is that we have to support all possible types
  // here. that's why matching trees rather than types is nice --
  // there are fewer shapes we have to support.
  //
  // we don't have to match the "left-hand side" arguments here, only
  // the "right-hand side" ones.
  //
  // for example, suppose we wanted to rewrite something like:
  //
  //   new WeirdOps(x, y, z)(ev0, ev1).method(u, v)(ev2)
  //
  // we would use the op21 method, since .method has two argument
  // lists, the first with 2 arguments, the second with 1.
  //
  // it's also worth nothing that the names *must* line up in
  // macros. this means that you'll be using names like x1, x2, y1,
  // and so on.

  def op00(): Tree = interpret()
  def op01()(y1: Tree): Tree = interpret()
  def op02()(y1: Tree, y2: Tree): Tree = interpret()
  def op03()(y1: Tree, y2: Tree, y3: Tree): Tree = interpret()

  def op10(x1: Tree): Tree = interpret()
  def op11(x1: Tree)(y1: Tree): Tree = interpret()
  def op12(x1: Tree)(y1: Tree, y2: Tree): Tree = interpret()
  def op13(x1: Tree)(y1: Tree, y2: Tree, y3: Tree): Tree = interpret()

  def op20(x1: Tree, x2: Tree): Tree = interpret()
  def op21(x1: Tree, x2: Tree)(y1: Tree): Tree = interpret()
  def op22(x1: Tree, x2: Tree)(y1: Tree, y2: Tree): Tree = interpret()
  def op23(x1: Tree, x2: Tree)(y1: Tree, y2: Tree, y3: Tree): Tree = interpret()
  
  def op30(x1: Tree, x2: Tree, x3: Tree): Tree = interpret()
  def op31(x1: Tree, x2: Tree, x3: Tree)(y1: Tree): Tree = interpret()
  def op32(x1: Tree, x2: Tree, x3: Tree)(y1: Tree, y2: Tree): Tree = interpret()
  def op33(x1: Tree, x2: Tree, x3: Tree)(y1: Tree, y2: Tree, y3: Tree): Tree = interpret()
}

/**
 * The op annotation is used to pass along rewriting information that
 * the macro implementation needs to produce new trees.
 *
 * The long-term vision is that a simulacrum-like macro would be
 * generating the implicit Ops class, including its methods and @op
 * annotations. A different (but related) annotation would be needed
 * to guide simulacrum's decisions about creating this Ops class.
 */
final class op(final val proto: String) extends StaticAnnotation

// some examples to prove this stuff kind of works
object Example {

  trait Semigroup[A] {
    def combine(x: A, y: A): A
  }

  object Semigroup {

    // our own optimized apply -- equivalent to imp's summon
    @op("$y {$x}")
    def apply[A](implicit x1: Semigroup[A]): Semigroup[A] =
      macro OpsMacros.op10

    implicit val intSemigroup: Semigroup[Int] =
      new Semigroup[Int] {
        def combine(x: Int, y: Int): Int = x + y
      }

    implicit class Ops[A](a: A)(implicit ev: Semigroup[A]) {
      // testing the semigroup's combine operator
      @op("$y.combine($x, $z)")
      def |+|(x1: A): A = macro OpsMacros.op10
    }
  }

  import scala.math.Ordering

  implicit class TestOps[A](a: A)(implicit ev: Ordering[A]) {
    // testing a comparison operator on the existing ordering type class
    @op("$y.compare($x, $z)")
    def <=>(x1: A): Int = macro OpsMacros.op10
  }

  // our own optimized "implicitly" -- equivalent to imp's imp
  @op("$y {$x}")
  def implicitly_[T](implicit x1: T): T =
    macro OpsMacros.op10

}
