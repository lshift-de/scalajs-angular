package com.greencatsoft.angularjs

import scala.language.implicitConversions
import scala.language.experimental.macros
import scala.scalajs.js
import scala.scalajs.js.Any.{ fromBoolean, fromFunction2, fromFunction4, fromString, wrapArray }
import scala.scalajs.js.UndefOr
import scala.scalajs.js.UndefOr.undefOr2ops
import scala.scalajs.js.annotation.JSBracketAccess

import org.scalajs.dom.Element

import com.greencatsoft.angularjs.core.{ Scope, ScopeOps }
import com.greencatsoft.angularjs.internal.{ ConfigBuilder, Configuration, ServiceProxy }

trait Directive extends Service with Function0[Configuration] with ScopeOps with ConfigBuilder {

  import internal.{ Angular => angular }

  type ScopeType <: Scope

  override def apply(): Configuration = buildConfig()

  override def buildConfig(config: Configuration): Configuration = {
    def bind(scope: ScopeType): ScopeType = {
      scope.dynamic.directive = this.asInstanceOf[js.Object]
      scope
    }

    def toController(obj: js.Any): Either[Controller[_ <: Scope], js.Any] =
      ServiceProxy.unbind[Controller[_ <: Scope]](obj) match {
        case Some(c) => Left(c)
        case None => Right(obj)
      }

    config("link") = (scope: ScopeType, elems: js.Array[Element], attrs: Attributes, controllers: UndefOr[js.Any]) => {
      controllers.toOption match {
        case Some(arr) if js.Array.isArray(arr) =>
          val args = arr.asInstanceOf[js.Array[js.Any]].toSeq.map(toController)
          link(bind(scope), elems, attrs, args: _*)
        case Some(c) =>
          ServiceProxy.unbind[Controller[_ <: Scope]](c) match {
            case Some(arg) => link(bind(scope), elems, attrs, Left(arg))
            case _ => link(bind(scope), elems, attrs, Right(c))
          }
        case None => link(bind(scope), elems, attrs)
      }
    }

    controller.foreach(config("controller") = _)

    super.buildConfig(config)
  }

  def controller: Option[js.Any] = None

  def link(scope: ScopeType, elems: Seq[Element], attrs: Attributes): Unit = Unit

  def link(scope: ScopeType, elems: Seq[Element], attrs: Attributes, controller: Either[Controller[_], js.Any]*): Unit = link(scope, elems, attrs)
}

@js.native
trait Attributes extends js.Object {

  val $attr: js.Dictionary[String] = js.native

  def $addClass(classVal: String): Unit = js.native

  def $removeClass(classVal: String): Unit = js.native

  def $updateClass(newClasses: String, oldClasses: String): Unit = js.native

  @JSBracketAccess
  def apply(name: String): UndefOr[String] = js.native

  @JSBracketAccess
  def update(name: String, value: String): Unit = js.native

  def $get(name: String): UndefOr[String] = js.native

  def $set(name: String, value: String): Unit = js.native

  def $observe(key: String, fn: js.Function1[String, Unit]): Unit = js.native
}

trait Requires extends ConfigBuilder {
  this: Directive =>

  var requirements = Set.empty[Requirement]

  abstract override def buildConfig(config: Configuration): Configuration = {
    config("require") = js.Array[String](requirements.toSeq.map(_.toString): _*)

    super.buildConfig(config)
  }

  case class Requirement(name: String, lookup: Boolean, optional: Boolean = false) {

    override def toString = (if (lookup) "^" else "") + (if (optional) "?" else "") + name
  }

  def ^(requirement: String) = new Requirement(requirement, true)

  def ^?(requirement: String) = new Requirement(requirement, true, true)

  def ?(requirement: String) = new Requirement(requirement, false, true)

  implicit def ~(requirement: String) = new Requirement(requirement, false)
}

trait RestrictedDirective extends Directive with ConfigBuilder {

  def restrict: Set[String] = Set.empty

  abstract override def buildConfig(config: Configuration): Configuration = {
    config("restrict") = restrict.mkString

    super.buildConfig(config)
  }
}

trait ElementDirective extends RestrictedDirective {

  override def restrict = super.restrict + "E"

  var transclude = false

  var replace = false

  abstract override def buildConfig(config: Configuration): Configuration = {
    config("transclude") = transclude
    config("replace") = replace

    super.buildConfig(config)
  }
}

trait AttributeDirective extends RestrictedDirective {

  override def restrict = super.restrict + "A"
}

trait ClassDirective extends RestrictedDirective {

  override def restrict = super.restrict + "C"
}

trait CommentDirective extends RestrictedDirective {

  override def restrict = super.restrict + "M"
}

trait ScopeStrategy extends ConfigBuilder {
  this: Directive =>
}

trait InheritParentScope extends ScopeStrategy {
  this: Directive =>

  override def buildConfig(config: Configuration): Configuration = {
    config("scope") = true

    super.buildConfig(config)
  }
}

trait UseParentScope extends ScopeStrategy {
  this: Directive =>

  override def buildConfig(config: Configuration): Configuration = {
    config("scope") = false

    super.buildConfig(config)
  }
}

trait IsolatedScope extends ScopeStrategy {
  this: Directive =>

  var bindings = Seq.empty[ScopeBinding]

  override def buildConfig(config: Configuration): Configuration = {
    val dict = js.Dictionary.empty[String]

    bindings foreach { b =>
      dict(b.name) = s"${b.prefix}${b.attribute}"
    }

    config("scope") = dict

    super.buildConfig(config)
  }

  sealed abstract class ScopeBinding(val prefix: String) {

    val name: String

    val attribute: String
  }

  case class UnidirectionalBinding(name: String, attribute: String = "") extends ScopeBinding("=")

  case class OptionalUnidirectionalBinding(name: String, attribute: String = "") extends ScopeBinding("=?")

  case class BidirectionalBinding(name: String, attribute: String = "") extends ScopeBinding("@")

  case class BehavioralBinding(name: String, attribute: String = "") extends ScopeBinding("&")

  implicit class BindingBuilder(name: String) {

    def :=(attribute: String = ""): ScopeBinding = UnidirectionalBinding(name, attribute)

    def :@(attribute: String = ""): ScopeBinding = BidirectionalBinding(name, attribute)

    def :&(attribute: String = ""): ScopeBinding = BehavioralBinding(name, attribute)

    def :=?(attribute: String = ""): ScopeBinding = OptionalUnidirectionalBinding(name, attribute)
  }
}

trait TemplatedDirective extends ConfigBuilder with Templated {
  this: Directive =>

  def getTemplateUrl(elems: Seq[AngularElement], attrs: Attributes): String = templateUrl

  abstract override def buildConfig(config: Configuration): Configuration = {
    config("templateUrl") = (elems: js.Array[AngularElement], attrs: Attributes) => getTemplateUrl(elems, attrs)

    super.buildConfig(config)
  }
}

trait TemplateSourceDirective extends ConfigBuilder with TemplateSource {
  this: Directive =>

  val template: String

  def getTemplate(elems: Seq[AngularElement], attrs: Attributes): String = template

  abstract override def buildConfig(config: Configuration): Configuration = {
    config("template") = (elems: js.Array[AngularElement], attrs: Attributes) => getTemplate(elems, attrs)

    super.buildConfig(config)
  }
}

