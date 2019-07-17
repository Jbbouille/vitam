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
package fr.gouv.vitam.storage.offers.tape.cas;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import fr.gouv.vitam.common.LocalDateUtil;
import fr.gouv.vitam.common.ParametersChecker;
import fr.gouv.vitam.common.database.server.query.QueryCriteria;
import fr.gouv.vitam.common.database.server.query.QueryCriteriaOperator;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.logging.VitamLogger;
import fr.gouv.vitam.common.logging.VitamLoggerFactory;
import fr.gouv.vitam.common.model.MetadatasObject;
import fr.gouv.vitam.common.storage.ContainerInformation;
import fr.gouv.vitam.common.storage.cas.container.api.ContentAddressableStorage;
import fr.gouv.vitam.common.storage.cas.container.api.MetadatasStorageObject;
import fr.gouv.vitam.common.storage.cas.container.api.ObjectContent;
import fr.gouv.vitam.common.storage.cas.container.api.VitamPageSet;
import fr.gouv.vitam.common.storage.cas.container.api.VitamStorageMetadata;
import fr.gouv.vitam.common.storage.constants.ErrorMessage;
import fr.gouv.vitam.storage.engine.common.model.ReadOrder;
import fr.gouv.vitam.storage.engine.common.model.TapeCatalog;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryInputFileObjectStorageLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryObjectReferentialId;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryObjectStorageLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryOnTapeArchiveStorageLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryTarObjectStorageLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeLibraryArchiveStorageLocation;
import fr.gouv.vitam.storage.engine.common.model.TapeObjectReferentialEntity;
import fr.gouv.vitam.storage.engine.common.model.TapeArchiveReferentialEntity;
import fr.gouv.vitam.storage.engine.common.model.TarEntryDescription;
import fr.gouv.vitam.storage.engine.common.utils.ContainerUtils;
import fr.gouv.vitam.storage.offers.tape.exception.ObjectReferentialException;
import fr.gouv.vitam.storage.offers.tape.exception.QueueException;
import fr.gouv.vitam.storage.offers.tape.exception.TapeCatalogException;
import fr.gouv.vitam.storage.offers.tape.exception.ArchiveReferentialException;
import fr.gouv.vitam.storage.offers.tape.spec.QueueRepository;
import fr.gouv.vitam.storage.offers.tape.spec.TapeCatalogService;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageNotFoundException;
import fr.gouv.vitam.workspace.api.exception.ContentAddressableStorageServerException;
import org.apache.commons.collections4.CollectionUtils;

public class TapeLibraryContentAddressableStorage implements ContentAddressableStorage {

    private static final VitamLogger LOGGER =
        VitamLoggerFactory.getInstance(TapeLibraryContentAddressableStorage.class);

    private final BasicFileStorage basicFileStorage;
    private final ObjectReferentialRepository objectReferentialRepository;
    private final FileBucketTarCreatorManager fileBucketTarCreatorManager;
    private final QueueRepository readWriteQueue;
    private final ArchiveReferentialRepository archiveReferentialRepository;
    private final TapeCatalogService tapeCatalogService;

    public TapeLibraryContentAddressableStorage(
        BasicFileStorage basicFileStorage,
        ObjectReferentialRepository objectReferentialRepository,
        ArchiveReferentialRepository archiveReferentialRepository,
        FileBucketTarCreatorManager fileBucketTarCreatorManager,
        QueueRepository readWriteQueue, TapeCatalogService tapeCatalogService) {
        this.basicFileStorage = basicFileStorage;
        this.objectReferentialRepository = objectReferentialRepository;
        this.archiveReferentialRepository = archiveReferentialRepository;
        this.fileBucketTarCreatorManager = fileBucketTarCreatorManager;
        this.readWriteQueue = readWriteQueue;
        this.tapeCatalogService = tapeCatalogService;
    }

    @Override
    public void createContainer(String containerName) {
        // NOP
    }

    @Override
    public boolean isExistingContainer(String containerName) {
        return true;
    }

    @Override
    public String putObject(String containerName, String objectName, InputStream stream, DigestType digestType,
        Long size) throws ContentAddressableStorageException {
        LOGGER.debug(String.format("Upload object %s in container %s", objectName, containerName));


        Digest digest = new Digest(digestType);
        InputStream digestInputStream = digest.getDigestInputStream(stream);

        // Persist to disk
        String storageId;
        try {
            storageId = this.basicFileStorage.writeFile(containerName, objectName, digestInputStream, size);
        } catch (IOException e) {
            throw new ContentAddressableStorageException(
                "Could not persist file to disk " + containerName + "/" + objectName, e);
        }
        String digestValue = digest.digestHex();

        // Commit to mongo
        try {
            String now = LocalDateUtil.now().toString();
            objectReferentialRepository.insertOrUpdate(new TapeObjectReferentialEntity(
                new TapeLibraryObjectReferentialId(containerName, objectName),
                size, digestType.getName(), digestValue,
                storageId, new TapeLibraryInputFileObjectStorageLocation(),
                now, now
            ));
        } catch (ObjectReferentialException ex) {
            throw new ContentAddressableStorageServerException(
                String.format("Could not index the object %s in container %s in database", objectName, containerName),
                ex);
        } finally {
            // Notify tar file builder queue
            fileBucketTarCreatorManager.addToQueue(
                new InputFileToProcessMessage(containerName, objectName, storageId, size, digestValue,
                    digestType.getName()));
        }

        // All done
        return digestValue;
    }

    @Override
    public ObjectContent getObject(String containerName, String objectName) {
        LOGGER.debug(String.format("Download object %s from container %s", objectName, containerName));
        throw new UnsupportedOperationException("To be implemented");
    }

    @Override
    public void asyncGetObject(String containerName, String objectName)
            throws ContentAddressableStorageServerException, ContentAddressableStorageNotFoundException {
        try {
            Optional<TapeObjectReferentialEntity> object = objectReferentialRepository.find(containerName, objectName);
            if (!object.isPresent()) {
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND + containerName + "/" + objectName);
            }

            // get TARs containing the object segments
            TapeLibraryObjectStorageLocation location = object.get().getLocation();
            if (!(location instanceof TapeLibraryTarObjectStorageLocation)) {
                // FIXME: 29/03/19
                throw new UnsupportedOperationException("Object in stored in tar. Not implemented yet");
            }

            List<TarEntryDescription> tarEntryDescriptions = ((TapeLibraryTarObjectStorageLocation) location).getTarEntries();

            if (tarEntryDescriptions == null || tarEntryDescriptions.isEmpty()) {
                throw new IllegalStateException("empty TAR description for object : " + containerName + "/" + objectName);
            }

            Set<String> tarIds = tarEntryDescriptions.stream()
                    .map(TarEntryDescription::getTarFileId)
                    .collect(Collectors.toSet());

            for (String tarId : tarIds) {

                Optional<TapeArchiveReferentialEntity> tapeLibraryTarReferentialEntity = archiveReferentialRepository.find(tarId);
                if (!tapeLibraryTarReferentialEntity.isPresent()) {
                    throw new IllegalStateException("TAR information not found for tarId : " + tarId  + " object :" +  containerName + "/" + objectName);
                }

                TapeLibraryArchiveStorageLocation tarLocation = tapeLibraryTarReferentialEntity.get().getLocation();
                if (!(tarLocation instanceof TapeLibraryOnTapeArchiveStorageLocation)) {
                    // FIXME: 29/03/19
                    throw new UnsupportedOperationException("Tar file not on tape. Not implemented yet");
                }

                // create read orders
                String tapeCode = ((TapeLibraryOnTapeArchiveStorageLocation)tarLocation).getTapeCode();
                Integer filePosition = ((TapeLibraryOnTapeArchiveStorageLocation)tarLocation).getFilePosition();
                String bucketId = getBucketByTapeCode(tapeCode);
                String fileName = tarId;
                ReadOrder readOrder = new ReadOrder(tapeCode, filePosition, fileName, bucketId);

                // add read orders to worker queue
                readWriteQueue.add(readOrder);
            }

        } catch (ObjectReferentialException | QueueException | ArchiveReferentialException | TapeCatalogException e) {
            throw new ContentAddressableStorageServerException(
                    "Error on reading object " + containerName + "/" + objectName);
        }
    }

    private String getBucketByTapeCode(String tapeCode) throws TapeCatalogException {

        List<TapeCatalog> tapeCatalogs = tapeCatalogService.find(
            Collections.singletonList(
                new QueryCriteria(TapeCatalog.CODE, tapeCode, QueryCriteriaOperator.EQ))
        );

        if (CollectionUtils.isEmpty(tapeCatalogs)) {
            throw new IllegalStateException("Unknown tape with code " + tapeCode);
        }

        if (tapeCatalogs.size() > 1) {
            throw new IllegalStateException("Multiple tapes with same code " + tapeCode);
        }

        String bucket = tapeCatalogs.get(0).getBucket();
        if (bucket == null) {
            throw new IllegalStateException("Unknown bucket for tape with code " + tapeCode);
        }
        return bucket;
    }

    @Override
    public void deleteObject(String containerName, String objectName)
        throws ContentAddressableStorageServerException, ContentAddressableStorageNotFoundException {
        LOGGER.debug(String.format("Delete object %s from container %s", objectName, containerName));

        try {
            boolean objectDeleted = this.objectReferentialRepository.delete(
                new TapeLibraryObjectReferentialId(containerName, objectName));

            if (!objectDeleted) {
                throw new ContentAddressableStorageNotFoundException(ErrorMessage.OBJECT_NOT_FOUND + objectName);
            }

        } catch (ObjectReferentialException e) {
            throw new ContentAddressableStorageServerException(
                "Error on deleting object " + containerName + "/" + objectName);
        }
    }

    @Override
    public boolean isExistingObject(String containerName, String objectName)
        throws ContentAddressableStorageServerException {

        LOGGER.debug(String.format("Check existence of object %s in container %s", objectName, containerName));
        try {
            Optional<TapeObjectReferentialEntity> objectReferentialEntity =
                objectReferentialRepository.find(containerName, objectName);
            return objectReferentialEntity.isPresent();
        } catch (ObjectReferentialException ex) {
            throw new ContentAddressableStorageServerException(
                String.format("Could not check existence of object %s in container %s", objectName, containerName), ex);
        }
    }

    @Override
    public String getObjectDigest(String containerName, String objectName, DigestType algo, boolean noCache)
        throws ContentAddressableStorageException {
        LOGGER.debug(String.format("Get digest of object %s in container %s", objectName, containerName));
        try {
            Optional<TapeObjectReferentialEntity> objectReferentialEntity =
                objectReferentialRepository.find(containerName, objectName);

            if (!objectReferentialEntity.isPresent()) {
                throw new ContentAddressableStorageNotFoundException(
                    String.format("No such object %s in container %s", objectName, containerName));
            }

            if (!algo.getName().equals(objectReferentialEntity.get().getDigestType())) {
                throw new ContentAddressableStorageNotFoundException(
                    String.format("Digest algorithm mismatch for object %s in container %s. Expected %s, found %s",
                        objectName, containerName, algo.getName(), objectReferentialEntity.get().getDigestType()));
            }

            return objectReferentialEntity.get().getDigest();

        } catch (ObjectReferentialException ex) {
            throw new ContentAddressableStorageServerException(
                String.format("Could not get digest of object %s in container %s", objectName, containerName), ex);
        }
    }

    @Override
    public ContainerInformation getContainerInformation(String containerName) {
        LOGGER.debug(String.format("Get information of container %s", containerName));
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_NAME_IS_A_MANDATORY_PARAMETER.getMessage(),
            containerName);
        // we do not call the storage since it is not pertinent in tape library storage
        final ContainerInformation containerInformation = new ContainerInformation();
        containerInformation.setUsableSpace(-1);
        return containerInformation;
    }

    @Override
    public MetadatasObject getObjectMetadata(String containerName, String objectName, boolean noCache)
        throws ContentAddressableStorageException {

        LOGGER.debug(String.format("Get metadata of object %s in container %s", objectName, containerName));
        ParametersChecker.checkParameter(ErrorMessage.CONTAINER_OBJECT_NAMES_ARE_A_MANDATORY_PARAMETER.getMessage(),
            containerName, objectName);

        try {
            Optional<TapeObjectReferentialEntity> objectReferentialEntity =
                objectReferentialRepository.find(containerName, objectName);

            if (!objectReferentialEntity.isPresent()) {
                throw new ContentAddressableStorageNotFoundException(
                    String.format("No such object %s in container %s", objectName, containerName));
            }

            MetadatasStorageObject result = new MetadatasStorageObject();
            result.setType(ContainerUtils.parseDataCategoryFromContainerName(containerName).getFolder());
            result.setObjectName(objectName);
            result.setDigest(objectReferentialEntity.get().getDigest());
            result.setFileSize(objectReferentialEntity.get().getSize());
            result.setLastModifiedDate(objectReferentialEntity.get().getLastObjectModifiedDate());
            return result;

        } catch (ObjectReferentialException ex) {
            throw new ContentAddressableStorageServerException(
                String.format("Could not get metadata of object %s in container %s", objectName, containerName), ex);
        }
    }

    @Override
    public VitamPageSet<? extends VitamStorageMetadata> listContainer(String containerName) {
        LOGGER.debug(String.format("Listing of object in container %s", containerName));
        throw new UnsupportedOperationException("To be implemented");
    }

    @Override
    public VitamPageSet<? extends VitamStorageMetadata> listContainerNext(String containerName, String nextMarker) {
        LOGGER.debug(String.format("Listing of object from marker %s in container %s", nextMarker, containerName));
        throw new UnsupportedOperationException("To be implemented");
    }

    @Override
    public void close() {
        // NOP
    }
}