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
package fr.gouv.vitam.processing.distributor.api;

import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.processing.Step;
import fr.gouv.vitam.processing.common.model.PauseRecover;
import fr.gouv.vitam.processing.common.model.ProcessStep;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;

/**
 * interface ProcessDistributor</br>
 * </br>
 * improves a availability and scalability </br>
 * Various methods Distributor engine
 */
public interface ProcessDistributor extends AutoCloseable {

    String UNITS_LEVEL = "UnitsLevel";
    String JSON_EXTENSION = ".json";
    String EXCEPTION_MESSAGE = "runtime exceptions thrown by the Process distributor during runnig...";
    String INGEST_LEVEL_STACK = "ingestLevelStack.json";
    String OBJECTS_LIST_EMPTY = "OBJECTS_LIST_EMPTY";
    String ELEMENT_UNITS = "Units";
    String DISTRIBUTOR_INDEX = "distributorIndex";
    String NOLEVEL = "_no_level";
    String WORKER_CALL_EXCEPTION = "WorkerCallException";

    /**
     * Distribute different steps (execute a workflow actions step by step)
     *
     * @param workParams {@link fr.gouv.vitam.processing.common.parameter.WorkerParameters} null not allowed
     * @param step {@link ProcessStep} null not allowed
     * @param operationId operationId
     * @param pauseRecover
     *
     * @return CompositeItemStatus : list of action response
     */
    ItemStatus distribute(WorkerParameters workParams, Step step, String operationId, PauseRecover pauseRecover);


    /**
     * Get the current step of the processWorkflow corresponding to the operationId
     * and update his pauseCancelAction parameter to be PauseOrCancelAction.ACTION_PAUSE
     * WorkerTask check this parameter and can pause the not yet running tasks
     * @param operationId concerning operation id
     * @return true if pause applied, false else
     */
    boolean pause(String operationId);

    /**
     * Get the current step if the processWorkflow corresponding to the operationId
     * and update his pauseCancelAction parameter to be PauseOrCancelAction.ACTION_CANCEL
     * WorkerTask check this parameter and can cancel the not yet running tasks
     * @param operationId concerning operation id
     * @return true if pause applied, false else
     */
    boolean cancel(String operationId);

}
