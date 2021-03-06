package one.mir.state.reader

import one.mir.consensus.GeneratingBalanceProvider
import one.mir.features.BlockchainFeatures._
import one.mir.state.{EitherExt2, LeaseBalance}
import one.mir.state.diffs._
import one.mir.{NoShrink, TransactionGen}
import org.scalacheck.Gen
import org.scalatest.prop.PropertyChecks
import org.scalatest.{Matchers, PropSpec}
import one.mir.lagonaki.mocks.TestBlock.{create => block}
import one.mir.settings.TestFunctionalitySettings.Enabled
import one.mir.transaction.GenesisTransaction
import one.mir.transaction.lease.LeaseTransactionV2

class StateReaderEffectiveBalancePropertyTest extends PropSpec with PropertyChecks with Matchers with TransactionGen with NoShrink {
  property("No-interactions genesis account's effectiveBalance doesn't depend on depths") {
    val setup: Gen[(GenesisTransaction, Int, Int, Int)] = for {
      master <- accountGen
      ts     <- positiveIntGen
      genesis: GenesisTransaction = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      emptyBlocksAmt <- Gen.choose(1, 10)
      atHeight       <- Gen.choose(1, 20)
      confirmations  <- Gen.choose(1, 20)
    } yield (genesis, emptyBlocksAmt, atHeight, confirmations)

    forAll(setup) {
      case (genesis: GenesisTransaction, emptyBlocksAmt, atHeight, confirmations) =>
        val genesisBlock = block(Seq(genesis))
        val nextBlocks   = List.fill(emptyBlocksAmt - 1)(block(Seq.empty))
        assertDiffAndState(genesisBlock +: nextBlocks, block(Seq.empty)) { (_, newState) =>
          newState.effectiveBalance(genesis.recipient, atHeight, confirmations) shouldBe genesis.amount
        }
    }
  }

  property("Negative generating balance case") {
    val fs  = Enabled.copy(preActivatedFeatures = Map(SmartAccounts.id -> 0, SmartAccountTrading.id -> 0))
    val Fee = 100000
    val setup = for {
      master <- accountGen
      ts     <- positiveLongGen
      genesis = GenesisTransaction.create(master, ENOUGH_AMT, ts).explicitGet()
      leaser <- accountGen
      xfer1  <- transferGeneratorPV2(ts + 1, master, leaser.toAddress, ENOUGH_AMT / 3)
      lease1 = LeaseTransactionV2.signed(2, leaser, xfer1.amount - Fee, Fee, ts + 2, master.toAddress, leaser).explicitGet()
      xfer2 <- transferGeneratorPV2(ts + 3, master, leaser.toAddress, ENOUGH_AMT / 3)
      lease2 = LeaseTransactionV2.signed(2, leaser, xfer2.amount - Fee, Fee, ts + 4, master.toAddress, leaser).explicitGet()
    } yield (leaser, genesis, xfer1, lease1, xfer2, lease2)

    forAll(setup) {
      case (leaser, genesis, xfer1, lease1, xfer2, lease2) =>
        assertDiffAndState(Seq(block(Seq(genesis)), block(Seq(xfer1, lease1))), block(Seq(xfer2, lease2)), fs) { (_, state) =>
          val portfolio       = state.portfolio(lease1.sender)
          val expectedBalance = xfer1.amount + xfer2.amount - 2 * Fee
          portfolio.balance shouldBe expectedBalance
          GeneratingBalanceProvider.balance(state, fs, state.height, leaser) shouldBe 0
          portfolio.lease shouldBe LeaseBalance(0, expectedBalance)
          portfolio.effectiveBalance shouldBe 0
        }
    }
  }
}
