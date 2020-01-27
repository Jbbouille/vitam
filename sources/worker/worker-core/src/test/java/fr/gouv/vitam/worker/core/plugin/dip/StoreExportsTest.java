package fr.gouv.vitam.worker.core.plugin.dip;

import fr.gouv.vitam.common.VitamConfiguration;
import fr.gouv.vitam.common.guid.GUIDFactory;
import fr.gouv.vitam.common.thread.RunWithCustomExecutor;
import fr.gouv.vitam.common.thread.RunWithCustomExecutorRule;
import fr.gouv.vitam.common.thread.VitamThreadPoolExecutor;
import fr.gouv.vitam.common.thread.VitamThreadUtils;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;
import fr.gouv.vitam.storage.engine.client.StorageClient;
import fr.gouv.vitam.storage.engine.client.StorageClientFactory;
import fr.gouv.vitam.storage.engine.common.model.request.ObjectDescription;
import fr.gouv.vitam.worker.common.HandlerIO;
import fr.gouv.vitam.workspace.client.WorkspaceClient;
import fr.gouv.vitam.workspace.client.WorkspaceClientFactory;
import fr.gouv.vitam.workspace.common.CompressInformation;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;

import static fr.gouv.vitam.common.model.IngestWorkflowConstants.SEDA_FILE;
import static fr.gouv.vitam.storage.engine.common.model.DataCategory.REPORT;
import static fr.gouv.vitam.worker.core.plugin.dip.StoreExports.ARCHIVE_TRANSFER;
import static fr.gouv.vitam.worker.core.plugin.dip.StoreExports.DIP_CONTAINER;
import static fr.gouv.vitam.worker.core.plugin.dip.StoreExports.TRANSFER_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

public class StoreExportsTest {

    static String EXPORT_DIP = "EXPORT_DIP";

    @Rule
    public RunWithCustomExecutorRule runInThread = new RunWithCustomExecutorRule(
        VitamThreadPoolExecutor.getDefaultExecutor());

    @RunWithCustomExecutor
    @Test
    public void execute() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(2);
        String requestId = GUIDFactory.newRequestIdGUID(2).toString();
        VitamThreadUtils.getVitamSession().setRequestId(requestId);

        HandlerIO handlerIO = mock(HandlerIO.class);
        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        doReturn(workspaceClientFactory).when(handlerIO).getWorkspaceClientFactory();
        doReturn(workspaceClient).when(workspaceClientFactory).getClient();

        WorkerParameters params = mock(WorkerParameters.class);
        doReturn(requestId).when(params).getContainerName();
        doReturn(requestId).when(handlerIO).getContainerName();
        doReturn(EXPORT_DIP).when(params).getWorkflowIdentifier();

        StorageClientFactory storageClientFactory = mock(StorageClientFactory.class);
        StorageClient storageClient = mock(StorageClient.class);
        doReturn(storageClient).when(storageClientFactory).getClient();

        StoreExports storeExports = new StoreExports(storageClientFactory);

        // When
        storeExports.execute(params, handlerIO);

        // Then
        verify(workspaceClient).createContainer(DIP_CONTAINER);
        verify(workspaceClient).createFolder(DIP_CONTAINER, "2");

        ArgumentCaptor<CompressInformation> compressInformationArgumentCaptor =
            ArgumentCaptor.forClass(CompressInformation.class);
        verify(workspaceClient).compress(eq(requestId), compressInformationArgumentCaptor.capture());
        assertThat(compressInformationArgumentCaptor.getValue().getFiles())
            .isEqualTo(Arrays.asList(SEDA_FILE, StoreExports.CONTENT));
        assertThat(compressInformationArgumentCaptor.getValue().getOutputFile()).isEqualTo("2/" + requestId);
        assertThat(compressInformationArgumentCaptor.getValue().getOutputContainer()).isEqualTo(DIP_CONTAINER);
        verifyZeroInteractions(storageClient);
    }

    @RunWithCustomExecutor
    @Test
    public void WhenTransferWorkflowthenStoreOnTransfersContainer() throws Exception {
        VitamThreadUtils.getVitamSession().setTenantId(2);
        String requestId = GUIDFactory.newRequestIdGUID(2).toString();
        VitamThreadUtils.getVitamSession().setRequestId(requestId);

        HandlerIO handlerIO = mock(HandlerIO.class);
        WorkspaceClientFactory workspaceClientFactory = mock(WorkspaceClientFactory.class);
        WorkspaceClient workspaceClient = mock(WorkspaceClient.class);
        doReturn(workspaceClientFactory).when(handlerIO).getWorkspaceClientFactory();
        doReturn(workspaceClient).when(workspaceClientFactory).getClient();

        WorkerParameters params = mock(WorkerParameters.class);
        doReturn(requestId).when(params).getContainerName();
        doReturn(requestId).when(handlerIO).getContainerName();
        doReturn(ARCHIVE_TRANSFER).when(params).getWorkflowIdentifier();

        StorageClientFactory storageClientFactory = mock(StorageClientFactory.class);
        StorageClient storageClient = mock(StorageClient.class);
        doReturn(storageClient).when(storageClientFactory).getClient();

        StoreExports storeExports = new StoreExports(storageClientFactory);

        // When
        storeExports.execute(params, handlerIO);

        // Then
        verify(workspaceClient).createContainer(TRANSFER_CONTAINER);
        verify(workspaceClient).createFolder(TRANSFER_CONTAINER, "2");

        ArgumentCaptor<CompressInformation> compressInformationArgumentCaptor =
            ArgumentCaptor.forClass(CompressInformation.class);
        verify(workspaceClient).compress(eq(requestId), compressInformationArgumentCaptor.capture());
        assertThat(compressInformationArgumentCaptor.getValue().getFiles())
            .isEqualTo(Arrays.asList(SEDA_FILE, StoreExports.CONTENT));
        assertThat(compressInformationArgumentCaptor.getValue().getOutputFile()).isEqualTo("2/" + requestId);
        assertThat(compressInformationArgumentCaptor.getValue().getOutputContainer()).isEqualTo(TRANSFER_CONTAINER);


        ArgumentCaptor<ObjectDescription> objectDescriptionArgumentCaptor =
            ArgumentCaptor.forClass(ObjectDescription.class);
        String reportName = VitamThreadUtils.getVitamSession().getRequestId() + ".jsonl";
        verify(storageClient).storeFileFromWorkspace(eq(VitamConfiguration.getDefaultStrategy()), eq(REPORT),
            eq(reportName), objectDescriptionArgumentCaptor.capture());
        ObjectDescription description = objectDescriptionArgumentCaptor.getValue();
        assertThat(description.getWorkspaceContainerGUID()).isEqualTo(
            VitamThreadUtils.getVitamSession().getRequestId());
        assertThat(description.getWorkspaceObjectURI()).isEqualTo(reportName);
    }
}