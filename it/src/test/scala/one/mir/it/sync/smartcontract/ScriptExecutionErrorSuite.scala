package one.mir.it.sync.smartcontract

import one.mir.account.{AddressScheme, Alias}
import one.mir.it.api.SyncHttpApi._
import one.mir.it.sync.{minFee, setScriptFee}
import one.mir.it.transactions.BaseTransactionSuite
import one.mir.lang.v1.FunctionHeader
import one.mir.lang.v1.compiler.Terms
import one.mir.state._
import one.mir.transaction.CreateAliasTransactionV2
import one.mir.transaction.smart.SetScriptTransaction
import one.mir.transaction.smart.script.ScriptCompiler
import one.mir.transaction.smart.script.v1.ScriptV1
import one.mir.transaction.transfer.TransferTransactionV2
import org.scalatest.CancelAfterFailure

class ScriptExecutionErrorSuite extends BaseTransactionSuite with CancelAfterFailure {
  private val acc0 = pkByAddress(firstAddress)
  private val acc1 = pkByAddress(secondAddress)
  private val acc2 = pkByAddress(thirdAddress)
  private val ts   = System.currentTimeMillis()

  test("custom throw message") {
    val scriptSrc =
      """
        |match tx {
        |  case t : TransferTransaction =>
        |    let res = if isDefined(t.assetId) then extract(t.assetId) == base58'' else isDefined(t.assetId) == false
        |    res
        |  case s : SetScriptTransaction => true
        |  case other => throw("Your transaction has incorrect type.")
        |}
      """.stripMargin

    val compiled = ScriptCompiler(scriptSrc, isAssetScript = false).explicitGet()._1

    val tx = sender.signedBroadcast(SetScriptTransaction.selfSigned(1, acc2, Some(compiled), setScriptFee, ts).explicitGet().json())
    nodes.waitForHeightAriseAndTxPresent(tx.id)

    val alias = Alias.fromString(s"alias:${AddressScheme.current.chainId.toChar}:asdasdasdv").explicitGet()
    assertBadRequestAndResponse(
      sender.signedBroadcast(CreateAliasTransactionV2.selfSigned(acc2, 2, alias, minFee, ts).explicitGet().json()),
      "Your transaction has incorrect type."
    )
  }

  test("wrong type of script return value") {
    val script = ScriptV1(
      Terms.FUNCTION_CALL(
        FunctionHeader.Native(100),
        List(Terms.CONST_LONG(3), Terms.CONST_LONG(2))
      )
    ).explicitGet()

    val tx = sender.signAndBroadcast(
      SetScriptTransaction
        .selfSigned(SetScriptTransaction.supportedVersions.head, acc0, Some(script), setScriptFee, ts)
        .explicitGet()
        .json())
    nodes.waitForHeightAriseAndTxPresent(tx.id)

    assertBadRequestAndResponse(
      sender.signedBroadcast(
        TransferTransactionV2
          .selfSigned(2, None, acc0, acc1.toAddress, 1000, ts, None, minFee, Array())
          .explicitGet()
          .json()),
      "not a boolean"
    )
  }
}
