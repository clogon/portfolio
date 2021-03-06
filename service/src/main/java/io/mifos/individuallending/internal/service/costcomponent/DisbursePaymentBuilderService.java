/*
 * Copyright 2017 Kuelap, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.mifos.individuallending.internal.service.costcomponent;

import io.mifos.core.lang.ServiceException;
import io.mifos.individuallending.api.v1.domain.product.AccountDesignators;
import io.mifos.individuallending.api.v1.domain.workflow.Action;
import io.mifos.individuallending.internal.repository.CaseParametersEntity;
import io.mifos.individuallending.internal.service.DataContextOfAction;
import io.mifos.individuallending.internal.service.schedule.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * @author Myrle Krantz
 */
@Service
public class DisbursePaymentBuilderService implements PaymentBuilderService {
  private final ScheduledChargesService scheduledChargesService;
  private final LossProvisionChargesService lossProvisionChargesService;

  @Autowired
  public DisbursePaymentBuilderService(
      final ScheduledChargesService scheduledChargesService,
      final LossProvisionChargesService lossProvisionChargesService) {
    this.scheduledChargesService = scheduledChargesService;
    this.lossProvisionChargesService = lossProvisionChargesService;
  }

  @Override
  public PaymentBuilder getPaymentBuilder(
      final @Nonnull DataContextOfAction dataContextOfAction,
      final @Nullable BigDecimal requestedDisbursalSize,
      final LocalDate forDate,
      final RunningBalances runningBalances)
  {
    final BigDecimal currentBalance = runningBalances.getBalance(AccountDesignators.CUSTOMER_LOAN_PRINCIPAL);

    if (requestedDisbursalSize != null &&
        dataContextOfAction.getCaseParametersEntity().getBalanceRangeMaximum().compareTo(
            currentBalance.add(requestedDisbursalSize)) < 0)
      throw ServiceException.conflict("Cannot disburse over the maximum balance.");

    final CaseParametersEntity caseParameters = dataContextOfAction.getCaseParametersEntity();
    final String productIdentifier = dataContextOfAction.getProductEntity().getIdentifier();
    final int minorCurrencyUnitDigits = dataContextOfAction.getProductEntity().getMinorCurrencyUnitDigits();
    final List<ScheduledAction> scheduledActions = Collections.singletonList(new ScheduledAction(Action.DISBURSE, forDate));

    final BigDecimal disbursalSize;
    if (requestedDisbursalSize == null)
      disbursalSize = dataContextOfAction.getCaseParametersEntity().getBalanceRangeMaximum();
    else
      disbursalSize = requestedDisbursalSize;

    final List<ScheduledCharge> scheduledCharges = scheduledChargesService.getScheduledCharges(
        productIdentifier, scheduledActions);
    final Optional<ScheduledCharge> initialLossProvisionCharge = lossProvisionChargesService.getScheduledChargeForDisbursement(
        dataContextOfAction, forDate);
    initialLossProvisionCharge.ifPresent(scheduledCharges::add);

    return CostComponentService.getCostComponentsForScheduledCharges(
        scheduledCharges,
        caseParameters.getBalanceRangeMaximum(),
        runningBalances,
        dataContextOfAction.getCaseParametersEntity().getPaymentSize(),
        disbursalSize,
        BigDecimal.ZERO,
        dataContextOfAction.getInterest(),
        minorCurrencyUnitDigits,
        true);
  }

  public BigDecimal getLoanPaymentSizeForSingleDisbursement(
      final BigDecimal disbursementSize,
      final DataContextOfAction dataContextOfAction) {
    final List<ScheduledAction> hypotheticalScheduledActions = ScheduledActionHelpers.getHypotheticalScheduledActions(
        CostComponentService.today(),
        dataContextOfAction.getCaseParameters());
    final List<ScheduledCharge> hypotheticalScheduledCharges = scheduledChargesService.getScheduledCharges(
        dataContextOfAction.getProductEntity().getIdentifier(),
        hypotheticalScheduledActions);
    return CostComponentService.getLoanPaymentSize(
        disbursementSize,
        disbursementSize,
        dataContextOfAction.getInterest(),
        dataContextOfAction.getProductEntity().getMinorCurrencyUnitDigits(),
        hypotheticalScheduledCharges);
  }
}
