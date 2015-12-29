package com.greencatsoft.angularjs

import scala.language.experimental.macros
import scala.scalajs.js

import com.greencatsoft.angularjs.core.{ Scope, ScopeOps }
import com.greencatsoft.angularjs.internal.ServiceProxy

trait Constant[A <: Scope] extends Service with ScopeOps {

  def scope: A

  abstract override def initialize() {
    assert(scope != null, "Property 'scope' should not be null.")

    super.initialize()
    scope.dynamic.controller = this.asInstanceOf[js.Object]
  }
}

object Constant {

  def proxy[A <: Constant[_]]: js.Any = macro ServiceProxy.newClassWrapper[A]

  def proxy[A <: Constant[_]](target: A): js.Any = macro ServiceProxy.newObjectWrapper[A]
}

abstract class AbstractConstant[A <: Scope](override val scope: A) extends Constant[A]