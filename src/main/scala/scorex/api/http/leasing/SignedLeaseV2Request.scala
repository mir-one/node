package scorex.api.http.leasing

import io.swagger.annotations.ApiModelProperty
import scorex.account.{AddressOrAlias, PublicKeyAccount}
import scorex.api.http.BroadcastRequest
import scorex.transaction.lease.{LeaseTransactionV1, LeaseTransactionV2}
import scorex.transaction.{Proofs, ValidationError}

case class SignedLeaseV2Request(@ApiModelProperty(required = true)
                                version: Byte,
                                @ApiModelProperty(value = "Base58 encoded sender public key", required = true)
                                senderPublicKey: String,
                                @ApiModelProperty(required = true)
                                amount: Long,
                                @ApiModelProperty(required = true)
                                fee: Long,
                                @ApiModelProperty(value = "Recipient address", required = true)
                                recipient: String,
                                @ApiModelProperty(required = true)
                                timestamp: Long,
                                @ApiModelProperty(required = true)
                                proofs: List[String])
    extends BroadcastRequest {
  def toTx: Either[ValidationError, LeaseTransactionV1] =
    for {
      _sender     <- PublicKeyAccount.fromBase58String(senderPublicKey)
      _proofBytes <- proofs.traverse(s => parseBase58(s, "invalid proof", Proofs.MaxProofStringSize))
      _proofs     <- Proofs.create(_proofBytes)
      _recipient  <- AddressOrAlias.fromString(recipient)
      _t          <- LeaseTransactionV2.create(version, _sender, amount, fee, timestamp, _recipient, _proofs)
    } yield _t
}

object SignedLeaseV2Request {
  implicit val broadcastLeaseRequestReadsFormat: Format[SignedLeaseV2Request] = Json.format
}
