package fr.gouv.vitam.worker.core.plugin.reclassification;

import fr.gouv.vitam.common.error.VitamError;
import fr.gouv.vitam.common.model.ProcessQuery;
import fr.gouv.vitam.common.model.RequestResponseOK;
import fr.gouv.vitam.common.model.StatusCode;
import fr.gouv.vitam.common.model.processing.ProcessDetail;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClient;
import fr.gouv.vitam.processing.management.client.ProcessingManagementClientFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LightweightWorkflowLockTest {

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    @Mock
    ProcessingManagementClientFactory processingManagementClientFactory;

    @Mock
    ProcessingManagementClient processingManagementClient;

    @Before
    public void init() {
        doReturn(processingManagementClient).when(processingManagementClientFactory).getClient();
    }

    @Test
    public void listConcurrentReclassificationWorkflows_ResponseOK() throws Exception {

        // Given
        String workflowId = "workflow";
        String currentProcessId = "1234";

        ProcessingManagementClientFactory processingManagementClientFactory =
            mock(ProcessingManagementClientFactory.class);
        ProcessingManagementClient processingManagementClient =
            mock(ProcessingManagementClient.class);
        doReturn(processingManagementClient).when(processingManagementClientFactory).getClient();

        ProcessDetail processDetail1 = new ProcessDetail();
        processDetail1.setOperationId("SomeOtherProcessId");
        processDetail1.setGlobalState("PAUSE");
        processDetail1.setStepStatus("FATAL");

        ProcessDetail processDetail2 = new ProcessDetail();
        processDetail2.setOperationId(currentProcessId);
        processDetail2.setGlobalState("RUNNING");
        processDetail2.setStepStatus("OK");

        doReturn(new RequestResponseOK<>().addAllResults(Arrays.asList(processDetail1, processDetail2)))
            .when(processingManagementClient).listOperationsDetails(any());

        LightweightWorkflowLock lightweightWorkflowLock =
            new LightweightWorkflowLock(processingManagementClientFactory);

        // When
        List<ProcessDetail> processDetails =
            lightweightWorkflowLock.listConcurrentReclassificationWorkflows(workflowId, currentProcessId);

        // Then
        ArgumentCaptor<ProcessQuery> processQueryArgumentCaptor = ArgumentCaptor.forClass(ProcessQuery.class);
        verify(processingManagementClient).listOperationsDetails(processQueryArgumentCaptor.capture());
        assertThat(processDetails).hasSize(1);
        assertThat(processDetails.get(0)).isEqualTo(processDetail1);
    }

    @Test
    public void listConcurrentReclassificationWorkflows_VitamError() throws Exception {

        // Given
        doReturn(new VitamError("KO"))
            .when(processingManagementClient).listOperationsDetails(any());

        LightweightWorkflowLock lightweightWorkflowLock =
            new LightweightWorkflowLock(processingManagementClientFactory);

        // When / Then
        assertThatThrownBy(() -> lightweightWorkflowLock.listConcurrentReclassificationWorkflows("any", "any"))
            .isInstanceOf(ReclassificationException.class)
            .matches(e -> ((ReclassificationException)e).getStatusCode().equals(StatusCode.FATAL));
    }
}
