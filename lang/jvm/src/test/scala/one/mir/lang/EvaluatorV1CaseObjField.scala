package one.mir.lang

import cats.kernel.Monoid
import one.mir.lang.Common._
import one.mir.lang.Testing._
import one.mir.lang.ScriptVersion.Versions.V1
import one.mir.lang.v1.compiler.Terms._
import one.mir.lang.v1.evaluator.ctx.EvaluationContext._
import one.mir.lang.v1.evaluator.ctx._
import one.mir.lang.v1.evaluator.ctx.impl.PureContext
import one.mir.lang.v1.evaluator.ctx.impl.PureContext._
import one.mir.lang.v1.testing.ScriptGen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}

class EvaluatorV1CaseObjField extends PropSpec with PropertyChecks with Matchers with ScriptGen with NoShrink {

  def context(p: CaseObj): EvaluationContext = Monoid.combine(PureContext.build(V1).evaluationContext, sampleUnionContext(p))

  property("case custom type field access") {
    ev[CONST_LONG](
      context = context(pointAInstance),
      expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), "X"), CONST_LONG(2L)))
    ) shouldBe evaluated(5)
  }

  property("case custom type field access over union") {
    def testAccess(instance: CaseObj, field: String) =
      ev[CONST_LONG](
        context = context(instance),
        expr = FUNCTION_CALL(sumLong.header, List(GETTER(REF("p"), field), CONST_LONG(2L)))
      )

    testAccess(pointAInstance, "X") shouldBe evaluated(5)
    testAccess(pointBInstance, "X") shouldBe evaluated(5)
    testAccess(pointAInstance, "YA") shouldBe evaluated(42)
    testAccess(pointBInstance, "YB") shouldBe evaluated(43)
  }
}