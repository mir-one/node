package one.mir.transaction.assets

import com.google.common.primitives.Bytes
import one.mir.crypto
import one.mir.state.ByteStr
import monix.eval.Coeval
import one.mir.account.{PrivateKeyAccount, PublicKeyAccount}
import one.mir.transaction._
import one.mir.crypto._
import scala.util.{Failure, Success, Try}

case class BurnTransactionV1 private (sender: PublicKeyAccount, assetId: ByteStr, quantity: Long, fee: Long, timestamp: Long, signature: ByteStr)
    extends BurnTransaction
    with SignedTransaction
    with FastHashId {

  override def version: Byte = 1

  override def chainByte: Option[Byte] = None

  override val builder: BurnTransactionV1.type = BurnTransactionV1

  override val bodyBytes: Coeval[Array[Byte]] = byteBase.map(base => Bytes.concat(Array(builder.typeId), base))
  override val bytes: Coeval[Array[Byte]]     = bodyBytes.map(body => Bytes.concat(body, signature.arr))
}

object BurnTransactionV1 extends TransactionParserFor[BurnTransactionV1] with TransactionParser.HardcodedVersion1 {

  override val typeId: Byte = BurnTransaction.typeId

  override protected def parseTail(version: Byte, bytes: Array[Byte]): Try[TransactionT] =
    Try {
      val (sender, assetId, quantity, fee, timestamp, end) = BurnTransaction.parseBase(0, bytes)
      val signature                                        = ByteStr(bytes.slice(end, end + SignatureLength))
      BurnTransactionV1
        .create(sender, assetId, quantity, fee, timestamp, signature)
        .fold(left => Failure(new Exception(left.toString)), right => Success(right))
    }.flatten

  def create(sender: PublicKeyAccount,
             assetId: ByteStr,
             quantity: Long,
             fee: Long,
             timestamp: Long,
             signature: ByteStr): Either[ValidationError, TransactionT] =
    BurnTransaction
      .validateBurnParams(quantity, fee)
      .map(_ => BurnTransactionV1(sender, assetId, quantity, fee, timestamp, signature))

  def signed(sender: PublicKeyAccount,
             assetId: ByteStr,
             quantity: Long,
             fee: Long,
             timestamp: Long,
             signer: PrivateKeyAccount): Either[ValidationError, TransactionT] =
    create(sender, assetId, quantity, fee, timestamp, ByteStr.empty).right.map { unverified =>
      unverified.copy(signature = ByteStr(crypto.sign(signer, unverified.bodyBytes())))
    }

  def selfSigned(sender: PrivateKeyAccount, assetId: ByteStr, quantity: Long, fee: Long, timestamp: Long): Either[ValidationError, TransactionT] =
    signed(sender, assetId, quantity, fee, timestamp, sender)
}
