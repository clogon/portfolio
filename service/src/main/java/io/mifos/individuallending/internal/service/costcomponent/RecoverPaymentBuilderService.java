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

import io.mifos.individuallending.api.v1.domain.workflow.Action;
import io.mifos.individuallending.internal.repository.CaseParametersEntity;
import io.mifos.individuallending.internal.service.DataContextOfAction;
import io.mifos.individuallending.internal.service.schedule.ScheduledAction;
import io.mifos.individuallending.internal.service.schedule.ScheduledCharge;
import io.mifos.individuallending.internal.service.schedule.ScheduledChargesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * @author Myrle Krantz
 */
@Service
public class RecoverPaymentBuilderService implements PaymentBuilderService {
  private final ScheduledChargesService scheduledChargesService;

  @Autowired
  public RecoverPaymentBuilderService(final ScheduledChargesService scheduledChargesService) {
    this.scheduledChargesService = scheduledChargesService;
  }

  @Override
  public PaymentBuilder getPaymentBuilder(
      final @Nonnull DataContextOfAction dataContextOfAction,
      final @Nullable BigDecimal requestedDisbursalSize,
      final LocalDate forDate,
      final RunningBalances runningBalances) {
    final CaseParametersEntity caseParameters = dataContextOfAction.getCaseParametersEntity();
    final String productIdentifier = dataContextOfAction.getProductEntity().getIdentifier();
    final int minorCurrencyUnitDigits = dataContextOfAction.getProductEntity().getMinorCurrencyUnitDigits();
    final List<ScheduledAction> scheduledActions = Collections.singletonList(new ScheduledAction(Action.RECOVER, forDate));
    final List<ScheduledCharge> scheduledCharges = scheduledChargesService.getScheduledCharges(
        productIdentifier, scheduledActions);

    final BigDecimal loanPaymentSize = dataContextOfAction.getCaseParametersEntity().getPaymentSize();

    return CostComponentService.getCostComponentsForScheduledCharges(
        scheduledCharges,
        caseParameters.getBalanceRangeMaximum(),
        runningBalances,
        loanPaymentSize,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        dataContextOfAction.getInterest(),
        minorCurrencyUnitDigits,
        true);
  }
}
