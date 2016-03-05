package typeclassic

import scala.annotation.{ compileTimeOnly, StaticAnnotation }
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

import macrocompat._

@compileTimeOnly("typeclass annotation should have been automatically removed but was not")
class typeclass(excludeParents: List[String] = Nil, generateAllOps: Boolean = true) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro TypeClassMacros.generateTypeClass
}


@bundle
class TypeClassMacros(val c: Context) {
  import c.universe._

  def generateTypeClass(annottees: c.Expr[Any]*): c.Expr[Any] = {
    annottees.map(_.tree) match {
      case (typeClass: ClassDef) :: Nil => modify(typeClass, None)
      case (typeClass: ClassDef) :: (companion: ModuleDef) :: Nil => modify(typeClass, Some(companion))
      case other :: Nil =>
        c.abort(c.enclosingPosition, "@typeclass can only be applied to traits or abstract classes that take 1 type parameter which is either a proper type or a type constructor")
    }
  }

  private def modify(typeClass: ClassDef, companion: Option[ModuleDef]) = {
    val (tparam, proper) = typeClass.tparams match {
      case hd :: Nil =>
        hd.tparams.size match {
          case 0 => (hd, true)
          case 1 => (hd, false)
          case n => c.abort(c.enclosingPosition, "@typeclass may only be applied to types that take a single proper type or type constructor")
        }
      case other => c.abort(c.enclosingPosition, "@typeclass may only be applied to types that take a single type parameter")
    }

    val modifiedTypeClass = typeClass // TODO

    val modifiedCompanion = generateCompanion(typeClass, tparam, proper, companion match {
      case Some(c) => c
      case None => q"object ${typeClass.name.toTermName} {}"
    })

    val result = c.Expr(q"""
      $modifiedTypeClass
      $modifiedCompanion
    """)

    trace(s"Generated type class ${typeClass.name}:\n" + showCode(result.tree))

    result
  }

  private def generateCompanion(typeClass: ClassDef, tparam0: TypeDef, proper: Boolean, comp: Tree): Tree = {
    val tparam = eliminateVariance(tparam0)

    val q"$mods object $name extends ..$bases { ..$body }" = comp

    q"""
      $mods object $name extends ..$bases {
        import scala.language.experimental.macros
        ..$body
        ${generateInstanceSummoner(typeClass, tparam)}
      }
    """
  }

  private def generateInstanceSummoner(typeClass: ClassDef, tparam: TypeDef): Tree = {
    q"""
      @_root_.typeclassic.op("$$y {$$x}")
      def apply[$tparam](implicit x1: ${typeClass.name}[${tparam.name}]): ${typeClass.name}[${tparam.name}] =
        macro _root_.typeclassic.OpsMacros.op10
    """
  }

  // This method is from simulacrum, contributed by paulp, and is licensed under 3-Clause BSD
  private def eliminateVariance(tparam: TypeDef): TypeDef = {
    // If there's another way to do this I'm afraid I don't know it.
    val u        = c.universe.asInstanceOf[c.universe.type with scala.reflect.internal.SymbolTable]
    val tparam0  = tparam.asInstanceOf[u.TypeDef]
    val badFlags = (Flag.COVARIANT | Flag.CONTRAVARIANT).asInstanceOf[Long]
    val fixedMods = tparam0.mods & ~badFlags
    TypeDef(fixedMods.asInstanceOf[c.universe.Modifiers], tparam.name, tparam.tparams, tparam.rhs)
  }

  private def trace(s: => String) = {
    // Macro paradise seems to always output info statements, even without -verbose
    if (sys.props.get("typeclassic.trace").isDefined) c.info(c.enclosingPosition, s, false)
  }
}
