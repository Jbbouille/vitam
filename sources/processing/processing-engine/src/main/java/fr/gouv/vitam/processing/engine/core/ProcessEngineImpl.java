/*******************************************************************************
 * Copyright French Prime minister Office/SGMAP/DINSIC/Vitam Program (2015-2019)
 *
 * contact.vitam@culture.gouv.fr
 *
 * This software is a computer program whose purpose is to implement a digital archiving back-office system managing
 * high volumetry securely and efficiently.
 *
 * This software is governed by the CeCILL 2.1 license under French law and abiding by the rules of distribution of free
 * software. You can use, modify and/ or redistribute the software under the terms of the CeCILL 2.1 license as
 * circulated by CEA, CNRS and INRIA at the following URL "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and rights to copy, modify and redistribute granted by the license,
 * users are provided only with a limited warranty and the software's author, the holder of the economic rights, and the
 * successive licensors have only limited liability.
 *
 * In this respect, the user's attention is drawn to the risks associated with loading, using, modifying and/or
 * developing or reproducing the software by the user in light of its specific status of free software, that may mean
 * that it is complicated to manipulate, and that also therefore means that it is reserved for developers and
 * experienced professionals having in-depth computer knowledge. Users are therefore encouraged to load and test the
 * software's suitability as regards their requirements in conditions enabling the security of their systems and/or data
 * to be ensured and, more generally, to use and operate it in the same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had knowledge of the CeCILL 2.1 license and that you
 * accept its terms.
 *******************************************************************************/
package fr.gouv.vitam.processing.engine.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.exception.InvalidGuidOperationException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.guid.GUIDReader;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.Action;
import fr.gouv.vitam.common.model.processing.PauseOrCancelAction;
import fr.gouv.vitam.common.parameter.ParameterHelper;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.logbook.common.MessageLogbookEngineHelper;
import fr.gouv.vitam.logbook.common.exception.LogbookClientBadRequestException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientNotFoundException;
import fr.gouv.vitam.logbook.common.exception.LogbookClientServerException;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationParameters;
import fr.gouv.vitam.logbook.common.parameters.LogbookOperationsClientHelper;
import fr.gouv.vitam.logbook.common.parameters.LogbookParameterName;
import fr.gouv.vitam.logbook.common.parameters.LogbookParametersFactory;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClient;
import fr.gouv.vitam.logbook.operations.client.LogbookOperationsClientFactory;
import fr.gouv.vitam.processing.common.automation.IEventsProcessEngine;
import fr.gouv.vitam.processing.common.exception.ProcessingEngineException;
import fr.gouv.vitam.processing.common.model.PauseRecover;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.processing.distributor.api.ProcessDistributor;
import fr.gouv.vitam.processing.engine.api.ProcessEngine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * ProcessEngineImpl class manages the context and call a process distributor
 */
public class ProcessEngineImpl implements ProcessEngine {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(ProcessEngineImpl.class);

    private static final String OBJECTS_LIST_EMPTY = "OBJECTS_LIST_EMPTY";

    private final Map<String, String> messageIdentifierMap = new HashMap<>();
    private final Map<String, String> prodserviceMap = new HashMap<>();
    private Map<String, String> engineParams = new HashMap<>();
    private WorkerParameters workerParameters = null;

    final GUID processId;
    private IEventsProcessEngine callback;
    private ProcessDistributor processDistributor;

    public ProcessEngineImpl(WorkerParameters workerParameters, ProcessDistributor processDistributor) {
        this.processDistributor = processDistributor;
        this.workerParameters = workerParameters;
        processId = GUIDFactory.newGUID();
        this.workerParameters.setProcessId(processId.getId());
    }

    @Override
    public void setCallback(IEventsProcessEngine callback) {
        this.callback = callback;
    }

    @Override
    public boolean pause(String operationId) {
        ParametersChecker.checkParameter("The parameter operationId is required", operationId);
        return this.processDistributor.pause(operationId);
    }

    @Override
    public boolean cancel(String operationId) {
        ParametersChecker.checkParameter("The parameter operationId is required", operationId);
        return this.processDistributor.cancel(operationId);
    }

    @Override
    public void start(ProcessStep step, WorkerParameters workerParameters, Map<String, String> params,
        PauseRecover pauseRecover)
        throws ProcessingEngineException {
        if (null == callback)
            throw new ProcessingEngineException("IEventsProcessEngine is required");
        if (null == step)
            throw new ProcessingEngineException("The paramter step cannot be null");;

        if (null != params)
            this.engineParams = params;
        if (null != workerParameters) {
            for (final WorkerParameterName key : workerParameters.getMapParameters().keySet()) {
                if (this.workerParameters.getParameterValue(key) == null) {
                    this.workerParameters.putParameterValue(key, workerParameters.getMapParameters().get(key));
                }
            }
        }

        // if the current state is completed or pause, do not start the step
        final String operationId = this.workerParameters.getContainerName();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Start Workflow: " + step.getId() + " Step:" + step.getStepName());
        }
        final int tenantId = ParameterHelper.getTenantParameter();
        final LogbookTypeProcess logbookTypeProcess = this.workerParameters.getLogbookTypeProcess();

        // Prepare the logbook operation
        LogbookOperationParameters parameters;
        try {
            parameters = logbookBeforeDistributorCall(step, this.workerParameters, tenantId, logbookTypeProcess);
        } catch (Exception e) {
            LOGGER.error("Logbook error while process workflow, do retry", e);
            try {
                parameters = logbookBeforeDistributorCall(step, this.workerParameters, tenantId, logbookTypeProcess);
            } catch (Exception ex) {
                LOGGER.error("Retry > Logbook error while process workflow", ex);
                throw new ProcessingEngineException(ex);
            }
        }
        final LogbookOperationParameters logbookParameter = parameters;

        // update the process monitoring for this step
        if (!PauseOrCancelAction.ACTION_RECOVER.equals(step.getPauseOrCancelAction())) {
            callback.onUpdate(StatusCode.STARTED);
        }

        this.workerParameters.setCurrentStep(step.getStepName());

        CompletableFuture
            // call distributor in async mode
            .supplyAsync(() -> {
                try {
                    return callDistributor(step, this.workerParameters, operationId, pauseRecover);

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, VitamThreadPoolExecutor.getDefaultExecutor())
            // When the distributor responds, finalize the logbook persistence
            .thenApply(distributorResponse -> {
                try {
                    // Do not log if stop or cancel occurs

                    ItemStatus pauseCancel =
                        distributorResponse.getItemsStatus().get(PauseOrCancelAction.ACTION_PAUSE.name());
                    if (null != pauseCancel) {
                        return distributorResponse;
                    }

                    pauseCancel = distributorResponse.getItemsStatus().get(PauseOrCancelAction.ACTION_CANCEL.name());
                    if (null != pauseCancel) {
                        return distributorResponse;
                    }

                    logbookAfterDistributorCall(step, this.workerParameters, tenantId, logbookTypeProcess,
                        logbookParameter, distributorResponse);
                    return distributorResponse;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
            // Finally handle event of evaluation (state and status and persistence/remove to/from workspace
            .thenApply(distributorResponse -> {
                ItemStatus pauseCancel =
                    distributorResponse.getItemsStatus().get(PauseOrCancelAction.ACTION_PAUSE.name());
                if (null != pauseCancel) {
                    callback.onPauseOrCancel(PauseOrCancelAction.ACTION_PAUSE, this.workerParameters);
                    return distributorResponse;
                }

                pauseCancel = distributorResponse.getItemsStatus().get(PauseOrCancelAction.ACTION_CANCEL.name());
                if (null != pauseCancel) {
                    callback.onPauseOrCancel(PauseOrCancelAction.ACTION_CANCEL, this.workerParameters);
                    return distributorResponse;
                }

                callback.onComplete(distributorResponse, this.workerParameters);
                return distributorResponse;
            })
            // When exception occurred
            .exceptionally((e) -> {
                LOGGER.error("Error while process workflow", e);
                callback.onError(e, this.workerParameters);
                return null;
            });
    }

    /**
     * Log operation before calling the distributor
     *
     * @param step
     * @param workParams
     * @param tenantId
     * @param logbookTypeProcess
     * @return
     * @throws InvalidGuidOperationException
     * @throws LogbookClientBadRequestException
     * @throws LogbookClientNotFoundException
     * @throws LogbookClientServerException
     */
    private LogbookOperationParameters logbookBeforeDistributorCall(ProcessStep step, WorkerParameters workParams,
        int tenantId, LogbookTypeProcess logbookTypeProcess)
        throws InvalidGuidOperationException, LogbookClientBadRequestException, LogbookClientNotFoundException,
        LogbookClientServerException {
        MessageLogbookEngineHelper messageLogbookEngineHelper = new MessageLogbookEngineHelper(logbookTypeProcess);
        LogbookOperationParameters parameters;
        parameters = LogbookParametersFactory.newLogbookOperationParameters(
            GUIDFactory.newEventGUID(tenantId),
            step.getStepName(),
            GUIDReader.getGUID(workParams.getContainerName()),
            logbookTypeProcess,
            StatusCode.STARTED,
            messageLogbookEngineHelper.getLabelOp(step.getStepName(), StatusCode.STARTED, null),
            GUIDReader.getGUID(workParams.getContainerName()));
        parameters.putParameterValue(
            LogbookParameterName.outcomeDetail,
            messageLogbookEngineHelper.getOutcomeDetail(step.getStepName(), StatusCode.STARTED));

        // do not re-save the logbook as saved before stop
        if (PauseOrCancelAction.ACTION_RECOVER.equals(step.getPauseOrCancelAction())) {
            return parameters;
        }

        try (final LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient()) {
            logbookClient.update(parameters);
        }
        return parameters;
    }



    /**
     * Call distributor to start the given step
     *
     * @param step
     * @param workParams
     * @param operationId
     * @param recoverFromStop should be true only for the first step to be executed after stop of the process workflow
     * @return
     */
    private ItemStatus callDistributor(ProcessStep step, WorkerParameters workParams, String operationId,
        PauseRecover pauseRecover) {
        final ItemStatus stepResponse = processDistributor.distribute(workParams, step, operationId, pauseRecover);
        try {
            processDistributor.close();
        } catch (final Exception exc) {
            LOGGER.warn(exc);
        }
        return stepResponse;
    }

    /**
     * Log operation after distributor response
     * 
     * @param step
     * @param workParams
     * @param tenantId
     * @param logbookTypeProcess
     * @param parameters
     * @param stepResponse
     * @throws InvalidGuidOperationException
     * @throws LogbookClientNotFoundException
     * @throws LogbookClientBadRequestException
     * @throws LogbookClientServerException
     * @throws JsonProcessingException
     * @throws InvalidParseOperationException
     */
    private void logbookAfterDistributorCall(ProcessStep step, WorkerParameters workParams, int tenantId,
        LogbookTypeProcess logbookTypeProcess, LogbookOperationParameters parameters, ItemStatus stepResponse)
        throws InvalidGuidOperationException, LogbookClientNotFoundException, LogbookClientBadRequestException,
        LogbookClientServerException, JsonProcessingException, InvalidParseOperationException {
        MessageLogbookEngineHelper messageLogbookEngineHelper = new MessageLogbookEngineHelper(logbookTypeProcess);
        final LogbookOperationsClientHelper helper = new LogbookOperationsClientHelper();
        for (final Action action : step.getActions()) {
            final String handlerId = action.getActionDefinition().getActionKey();
            // Each handler could have a list itself => ItemStatus
            final ItemStatus itemStatus = stepResponse.getItemsStatus().get(handlerId);
            if (itemStatus != null) {
                final LogbookOperationParameters actionParameters =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDFactory.newEventGUID(tenantId),
                        handlerId,
                        GUIDReader.getGUID(workParams.getContainerName()),
                        logbookTypeProcess,
                        StatusCode.STARTED,
                        messageLogbookEngineHelper.getLabelOp(handlerId, StatusCode.STARTED, null),
                        GUIDReader.getGUID(workParams.getContainerName()));
                actionParameters.putParameterValue(
                    LogbookParameterName.outcomeDetail, messageLogbookEngineHelper
                        .getOutcomeDetail(handlerId, StatusCode.STARTED));
                helper.updateDelegate(actionParameters);
                if (itemStatus instanceof ItemStatus) {
                    final ItemStatus actionStatus = itemStatus;
                    for (final ItemStatus sub : actionStatus.getItemsStatus().values()) {
                        final LogbookOperationParameters startParameters =
                            LogbookParametersFactory.newLogbookOperationParameters(
                                GUIDFactory.newEventGUID(tenantId),
                                handlerId + "." + sub.getItemId(),
                                GUIDReader.getGUID(workParams.getContainerName()),
                                logbookTypeProcess,
                                StatusCode.STARTED,
                                messageLogbookEngineHelper
                                    .getLabelOp(handlerId, sub.getItemId(), StatusCode.STARTED, null),
                                GUIDReader.getGUID(workParams.getContainerName()));

                        startParameters.putParameterValue(LogbookParameterName.outcomeDetail,
                            messageLogbookEngineHelper
                                .getOutcomeDetail(handlerId, sub.getItemId(), StatusCode.STARTED));
                        helper.updateDelegate(startParameters);

                        final LogbookOperationParameters sublogbook =
                            LogbookParametersFactory.newLogbookOperationParameters(
                                GUIDFactory.newEventGUID(tenantId),
                                handlerId + "." + sub.getItemId(),
                                GUIDReader.getGUID(workParams.getContainerName()),
                                logbookTypeProcess,
                                sub.getGlobalStatus(),
                                null, " Detail= " + sub.computeStatusMeterMessage(),
                                GUIDReader.getGUID(workParams.getContainerName()));

                        if (sub.getGlobalOutcomeDetailSubcode() != null) {
                            sublogbook.putParameterValue(LogbookParameterName.outcomeDetail,
                                messageLogbookEngineHelper.getOutcomeDetail(
                                    handlerId + "." + sub.getItemId() + "." + sub.getGlobalOutcomeDetailSubcode(),
                                    sub.getGlobalStatus()));
                            sublogbook.putParameterValue(LogbookParameterName.outcomeDetailMessage,
                                messageLogbookEngineHelper.getLabelOp(
                                    handlerId + "." + sub.getItemId() + "." + sub.getGlobalOutcomeDetailSubcode(),
                                    sub.getGlobalStatus()) + " Detail= " + sub.computeStatusMeterMessage());
                        }

                        helper.updateDelegate(sublogbook);
                    }
                }
                String itemId = null;
                if (!itemStatus.getItemId().equals(handlerId)) {
                    itemId = itemStatus.getItemId();
                }

                final LogbookOperationParameters sublogbook =
                    LogbookParametersFactory.newLogbookOperationParameters(
                        GUIDFactory.newEventGUID(tenantId),
                        handlerId,
                        GUIDReader.getGUID(workParams.getContainerName()),
                        logbookTypeProcess,
                        itemStatus.getGlobalStatus(),
                        itemId, " Detail= " + itemStatus.computeStatusMeterMessage(),
                        GUIDReader.getGUID(workParams.getContainerName()));
                if (itemStatus.getMasterData() != null) {
                    JsonNode value = JsonHandler.toJsonNode(itemStatus.getMasterData());
                    sublogbook.putParameterValue(LogbookParameterName.masterData, JsonHandler.writeAsString(value));
                }
                if (itemStatus.getEvDetailData() != null) {
                    final String eventDetailData =
                        itemStatus.getEvDetailData().toString();
                    sublogbook.putParameterValue(LogbookParameterName.eventDetailData, eventDetailData);
                }
                helper.updateDelegate(sublogbook);
            }
        }

        final ItemStatus itemStatusObjectListEmpty = stepResponse.getItemsStatus().get(OBJECTS_LIST_EMPTY);
        if (itemStatusObjectListEmpty != null) {
            final LogbookOperationParameters actionParameters =
                LogbookParametersFactory.newLogbookOperationParameters(
                    GUIDFactory.newEventGUID(tenantId),
                    OBJECTS_LIST_EMPTY,
                    GUIDReader.getGUID(workParams.getContainerName()),
                    logbookTypeProcess,
                    itemStatusObjectListEmpty.getGlobalStatus(),
                    messageLogbookEngineHelper
                        .getLabelOp(OBJECTS_LIST_EMPTY, itemStatusObjectListEmpty.getGlobalStatus()),
                    GUIDReader.getGUID(workParams.getContainerName()));
            helper.updateDelegate(actionParameters);
        }

        String messageIdentifier = messageIdentifierMap.get(processId);

        if (messageIdentifier == null) {
            if (stepResponse.getData(SedaConstants.TAG_MESSAGE_IDENTIFIER) != null) {
                messageIdentifier = stepResponse.getData(SedaConstants.TAG_MESSAGE_IDENTIFIER).toString();
                messageIdentifierMap.put(processId.getId(), messageIdentifier);
            }
        }

        String prodService = prodserviceMap.get(processId);
        if (prodService == null) {
            if (stepResponse.getData(LogbookParameterName.agentIdentifierOriginating.name()) != null) {
                prodService =
                    (String) stepResponse.getData(LogbookParameterName.agentIdentifierOriginating.name().toString());
                prodserviceMap.put(processId.getId(), prodService);
            }
        }

        if (messageIdentifier != null && !messageIdentifier.isEmpty()) {
            callback.onUpdate(messageIdentifier, null);
            parameters.putParameterValue(LogbookParameterName.objectIdentifierIncome, messageIdentifier);
        } else {
            parameters.putParameterValue(LogbookParameterName.objectIdentifierIncome,
                engineParams.get(SedaConstants.TAG_MESSAGE_IDENTIFIER));
        }

        if (prodService != null && !prodService.isEmpty()) {
            callback.onUpdate(null, prodService);
            parameters.putParameterValue(LogbookParameterName.agentIdentifierOriginating, prodService);
        } else {
            parameters.putParameterValue(LogbookParameterName.agentIdentifierOriginating,
                engineParams.get(SedaConstants.TAG_ORIGINATINGAGENCY));
        }

        parameters.putParameterValue(LogbookParameterName.eventIdentifier,
            GUIDFactory.newEventGUID(tenantId).getId());

        parameters.putParameterValue(LogbookParameterName.outcome,
            stepResponse.getGlobalStatus().name());

        parameters.putParameterValue(LogbookParameterName.outcomeDetail,
            messageLogbookEngineHelper.getOutcomeDetail(step.getStepName(), stepResponse.getGlobalStatus()));

        parameters.putParameterValue(LogbookParameterName.outcomeDetailMessage,
            messageLogbookEngineHelper.getLabelOp(stepResponse.getItemId(), stepResponse.getGlobalStatus()));

        helper.updateDelegate(parameters);
        try (final LogbookOperationsClient logbookClient = LogbookOperationsClientFactory.getInstance().getClient()) {
            logbookClient.bulkUpdate(workParams.getContainerName(),
                helper.removeUpdateDelegate(workParams.getContainerName()));
        }

        // update the process with the final status
        callback.onUpdate(stepResponse.getGlobalStatus());
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("End Workflow: " + step.getId() + " Step:" + step.getStepName());
        }
    }
}

