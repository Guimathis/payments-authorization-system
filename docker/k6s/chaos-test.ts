import { AuthorizationSystemClient } from "./authorizationSystem.ts";

const baseUrl = "http://localhost:8080";
const authorizationSystemClient = new AuthorizationSystemClient({ baseUrl });

export default function () {
  let paymentAuthorizationRequestDto, headers;

  /**
   * Autorizar pagamento
   */
  paymentAuthorizationRequestDto = {
    accountId: "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    merchantId: "7ca85f64-5717-4562-b3fc-2c963f66afb7",
    amount: "250",
    currency: "BRL",
    paymentMethod: "CREDIT_CARD",
    cardToken: "tok_visa_1234_sandbox",
  };
  headers = {
    "Idempotency-Key": "badly",
  };

  const authorizePaymentResponseData =
    authorizationSystemClient.authorizePayment(
      paymentAuthorizationRequestDto,
      headers,
    );
}
