/**
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
 */
package fr.gouv.vitam.worker.core.handler;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.BaseXx;
import fr.gouv.vitam.common.SedaConstants;
import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.json.JsonHandler;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.ItemStatus;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.security.merkletree.MerkleTree;
import fr.gouv.vitam.common.security.merkletree.MerkleTreeAlgo;
import fr.gouv.vitam.logbook.common.model.TraceabilityEvent;
import fr.gouv.vitam.processing.common.exception.ProcessingException;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.worker.common.HandlerIO;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * Using Merkle trees to detect inconsistencies in data
 */
public class VerifyMerkleTreeActionHandler extends ActionHandler {

    private static final VitamLogger LOGGER = VitamLoggerFactory.getInstance(VerifyMerkleTreeActionHandler.class);

    private static final String ROOT = "Root";

    private static final String MERKLE_TREE_JSON = "merkleTree.json";

    static final String DATA_FILE = "data.txt";

    private static final String HANDLER_ID = "CHECK_MERKLE_TREE";

    private static final String HANDLER_SUB_ACTION_COMPARE_WITH_SAVED_HASH = "COMPARE_MERKLE_HASH_WITH_SAVED_HASH";

    private static final String HANDLER_SUB_ACTION_COMPARE_WITH_INDEXED_HASH = "COMPARE_MERKLE_HASH_WITH_INDEXED_HASH";

    private static final int TRACEABILITY_EVENT_DETAIL_RANK = 0;
    private static final int END_OF_STREAM = -1;
    private static final char NEW_LINE_SEPARATOR = '\n';

    /**
     * @return HANDLER_ID
     */
    public static String getId() {
        return HANDLER_ID;
    }

    @Override
    public ItemStatus execute(WorkerParameters params, HandlerIO handler) {

        final ItemStatus itemStatus = new ItemStatus(HANDLER_ID);

        InputStream operationsInputStream = null;
        try {

            // 1- Get TraceabilityEventDetail from Workspace
            TraceabilityEvent traceabilityEvent =
                JsonHandler.getFromFile((File) handler.getInput(TRACEABILITY_EVENT_DETAIL_RANK),
                    TraceabilityEvent.class);

            // 2- Get data.txt file
            String operationFilePath = SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
                DATA_FILE;
            operationsInputStream = handler.getInputStreamFromWorkspace(operationFilePath);

            // 3- Calculate MerkelTree hash

            // TODO Get digest algorithm from traceabilityEvent object
            MerkleTreeAlgo merkleTreeAlgo = computeMerkleTree(operationsInputStream);

            // calculates hash
            final String currentRootHash = currentRootHash(merkleTreeAlgo);

            // compare to secured and indexed hash
            final ItemStatus subSecuredItem = compareToSecuredHash( handler, currentRootHash);
            itemStatus.setItemsStatus(HANDLER_SUB_ACTION_COMPARE_WITH_SAVED_HASH, subSecuredItem);

            final ItemStatus subLoggedItemStatus = compareToLoggedHash(params, currentRootHash, traceabilityEvent);
            itemStatus.setItemsStatus(HANDLER_SUB_ACTION_COMPARE_WITH_INDEXED_HASH, subLoggedItemStatus);

        } catch (Exception e) {
            LOGGER.error(e);
            itemStatus.increment(StatusCode.FATAL);
        } finally {
        	IOUtils.closeQuietly(operationsInputStream);
        }

        return new ItemStatus(HANDLER_ID).setItemsStatus(HANDLER_ID, itemStatus);
    }

    ItemStatus compareToLoggedHash(WorkerParameters params, final String currentRootHash,
        TraceabilityEvent traceabilityEvent) {
        final ItemStatus subLoggedItemStatus = new ItemStatus(HANDLER_SUB_ACTION_COMPARE_WITH_INDEXED_HASH);
        if (!currentRootHash.equals(traceabilityEvent.getHash())) {
            subLoggedItemStatus.increment(StatusCode.KO);
        }
        return subLoggedItemStatus.increment(StatusCode.OK);
    }


    private ItemStatus compareToSecuredHash( HandlerIO handler, final String currentRootHash)
        throws ProcessingException {

        final ItemStatus subItemStatus = new ItemStatus(HANDLER_SUB_ACTION_COMPARE_WITH_SAVED_HASH);

        String merkleTreeFile = SedaConstants.TRACEABILITY_OPERATION_DIRECTORY + "/" +
            MERKLE_TREE_JSON;

        final JsonNode merkleTree = handler.getJsonFromWorkspace(merkleTreeFile);

        final String securedRootHash = merkleTree.get(ROOT).asText();

        if (currentRootHash == null || !currentRootHash.equals(securedRootHash)) {
            subItemStatus.increment(StatusCode.KO);
        }
        return subItemStatus.increment(StatusCode.OK);
    }

    /**
     * @param merkleTreeAlgo
     * @return
     */
    String currentRootHash(final MerkleTreeAlgo merkleTreeAlgo) {
        // check ok then compare diff root hash
        final MerkleTree currentMerkleTree = merkleTreeAlgo.generateMerkle();

        return BaseXx.getBase64(currentMerkleTree.getRoot());
    }

    /**
     * Compute merkle tree
     *
     * @param inputStream
     * @return the computed Merkle tree
     * @throws ProcessingException
     */
    public static MerkleTreeAlgo computeMerkleTree(InputStream inputStream)
        throws ProcessingException {

        final MerkleTreeAlgo merkleTreeAlgo = new MerkleTreeAlgo(VitamConfiguration.getDefaultDigestType());

        // Process
        try (BufferedInputStream bis = new BufferedInputStream(inputStream);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {

            int c;
            while (END_OF_STREAM != (c = bis.read())) {
                if (c != NEW_LINE_SEPARATOR) {
                    buffer.write(c);
                } else {
                    merkleTreeAlgo.addLeaf(buffer.toByteArray());
                    buffer.reset();
                }
            }

            if (buffer.size() > 0) {
                // Add any remaining
                merkleTreeAlgo.addLeaf(buffer.toByteArray());
            }

        } catch (IOException e) {
            throw new ProcessingException(e);
        }

        return merkleTreeAlgo;
    }

    @Override
    public void checkMandatoryIOParameter(HandlerIO handler) throws ProcessingException {
        // TODO Auto-generated method stub
    }
}
