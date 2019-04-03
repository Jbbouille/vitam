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
package fr.gouv.vitam.storage.offers.tape;

import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.server.mongodb.MongoDbAccess;
import fr.gouv.vitam.common.exception.VitamRuntimeException;
import fr.gouv.vitam.common.storage.tapelibrary.TapeLibraryConfiguration;
import fr.gouv.vitam.storage.engine.common.collection.OfferCollections;
import fr.gouv.vitam.storage.offers.tape.cas.BasicFileStorage;
import fr.gouv.vitam.storage.offers.tape.cas.BucketTopologyHelper;
import fr.gouv.vitam.storage.offers.tape.cas.FileBucketTarCreatorManager;
import fr.gouv.vitam.storage.offers.tape.cas.ObjectReferentialRepository;
import fr.gouv.vitam.storage.offers.tape.cas.TapeLibraryContentAddressableStorage;
import fr.gouv.vitam.storage.offers.tape.cas.TarReferentialRepository;
import fr.gouv.vitam.storage.offers.tape.cas.WriteOrderCreator;
import fr.gouv.vitam.storage.offers.tape.spec.QueueRepository;
import org.apache.logging.log4j.util.Strings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TapeStorageFactory {


    public TapeLibraryContentAddressableStorage initialize(TapeLibraryConfiguration configuration,
        MongoDbAccess mongoDbAccess) throws IOException {
        ParametersChecker.checkParameter("All params are required", configuration, mongoDbAccess);
        createWorkingDirectories(configuration);
        BucketTopologyHelper bucketTopologyHelper = new BucketTopologyHelper(configuration.getTopology());

        ObjectReferentialRepository objectReferentialRepository =
            new ObjectReferentialRepository(mongoDbAccess.getMongoDatabase()
                .getCollection(OfferCollections.TAPE_OBJECT_REFERENTIAL.getName()));
        TarReferentialRepository tarReferentialRepository =
            new TarReferentialRepository(mongoDbAccess.getMongoDatabase()
                .getCollection(OfferCollections.TAPE_TAR_REFERENTIAL.getName()));

        TapeLibraryFactory tapeLibraryFactory = TapeLibraryFactory.getInstance();
        tapeLibraryFactory.initialize(configuration, mongoDbAccess);

        QueueRepository readWriteQueue = tapeLibraryFactory.getReadWriteQueue();

        // Change all running orders to ready state
        readWriteQueue.initializeOnBootstrap();

        WriteOrderCreator writeOrderCreator = new WriteOrderCreator(configuration, objectReferentialRepository,
            tarReferentialRepository, bucketTopologyHelper, readWriteQueue);
        writeOrderCreator.initializeOnBootstrap();

        BasicFileStorage basicFileStorage =
            new BasicFileStorage(configuration.getInputFileStorageFolder());
        FileBucketTarCreatorManager fileBucketTarCreatorManager =
            new FileBucketTarCreatorManager(configuration, basicFileStorage, bucketTopologyHelper,
                objectReferentialRepository, tarReferentialRepository, writeOrderCreator);
        fileBucketTarCreatorManager.initializeOnBootstrap();

        TapeLibraryContentAddressableStorage tapeLibraryContentAddressableStorage =
            new TapeLibraryContentAddressableStorage(basicFileStorage, objectReferentialRepository,
                tarReferentialRepository, fileBucketTarCreatorManager, readWriteQueue,
                tapeLibraryFactory.getTapeCatalogService());

        // Everything's alright. Start listeners
        writeOrderCreator.startListener();
        fileBucketTarCreatorManager.startListeners();

        return tapeLibraryContentAddressableStorage;
    }

    private void createWorkingDirectories(TapeLibraryConfiguration configuration) throws IOException {

        if (Strings.isBlank(configuration.getInputFileStorageFolder()) ||
            Strings.isBlank(configuration.getInputTarStorageFolder()) ||
            Strings.isBlank(configuration.getOutputTarStorageFolder())) {
            throw new VitamRuntimeException("Tape storage configuration");
        }
        createDirectory(configuration.getInputFileStorageFolder());
        createDirectory(configuration.getInputTarStorageFolder());
        createDirectory(configuration.getOutputTarStorageFolder());
    }

    private void createDirectory(String pathStr) throws IOException {
        Path path = Paths.get(pathStr);
        //if directory exists?
        if (!Files.exists(path)) {
            Files.createDirectories(path);
        }

    }
}
