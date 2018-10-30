package fr.gouv.vitam.worker.core.plugin.preservation;

import com.fasterxml.jackson.databind.JsonNode;
import fr.gouv.vitam.common.guid.GUID;
import fr.gouv.vitam.logbook.common.parameters.LogbookTypeProcess;
import fr.gouv.vitam.processing.common.parameter.WorkerParameterName;
import fr.gouv.vitam.processing.common.parameter.WorkerParameters;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.gouv.vitam.processing.common.parameter.WorkerParameterName.containerName;
import static fr.gouv.vitam.processing.common.parameter.WorkerParameterName.requestId;

public class TestWorkerParameter implements WorkerParameters {
    public final Map<String, String> params;

    public TestWorkerParameter(Map<String, String> params) {
        this.params = params;
    }


    @Override
    public WorkerParameters putParameterValue(WorkerParameterName parameterName, String parameterValue) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getParameterValue(WorkerParameterName parameterName) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setMap(Map<String, String> map) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getCurrentStep() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setCurrentStep(String currentStep) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getPreviousStep() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setPreviousStep(String previousStep) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getContainerName() {
        return this.params.get(containerName.name());
    }

    @Override
    public WorkerParameters setContainerName(String containerName) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getObjectId() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setObjectId(String objectId) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getObjectName() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setObjectName(String objectName) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public JsonNode getObjectMetadata() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setObjectMetadata(JsonNode objectName) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public List<String> getObjectNameList() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setObjectNameList(List<String> objectNameList) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public List<JsonNode> getObjectMetadataList() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setObjectMetadataList(List<JsonNode> objectMetadataList) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getMetadataRequest() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setMetadataRequest(String metadataRequest) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getWorkerGUID() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setWorkerGUID(GUID workerGUID) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getProcessId() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setProcessId(String processId) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getUrlMetadata() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setUrlMetadata(String urlMetadata) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getUrlWorkspace() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setUrlWorkspace(String urlWorkspace) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public LogbookTypeProcess getLogbookTypeProcess() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setLogbookTypeProcess(LogbookTypeProcess logbookTypeProcess) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public WorkerParameters setFromParameters(WorkerParameters parameters) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public String getRequestId() {
        return this.params.get(requestId.name());
    }

    @Override
    public WorkerParameters setRequestId(String newRequestId) {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Map<WorkerParameterName, String> getMapParameters() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Set<WorkerParameterName> getMandatoriesParameters() {
        throw new IllegalStateException("Not implemented");
    }

    public static final class TestWorkerParameterBuilder {
        public Map<String, String> params = new HashMap<>();

        private TestWorkerParameterBuilder() {
        }

        public static TestWorkerParameterBuilder workerParameterBuilder() {
            return new TestWorkerParameterBuilder();
        }

        public TestWorkerParameterBuilder withParams(Map<String, String> params) {
            this.params = params;
            return this;
        }

        public TestWorkerParameterBuilder withRequestId(String requestId) {
            this.params.put(WorkerParameterName.requestId.name(), requestId);
            return this;
        }

        public TestWorkerParameterBuilder withContainerName(String containerName) {
            this.params.put(WorkerParameterName.containerName.name(), containerName);
            return this;
        }

        public TestWorkerParameter build() {
            return new TestWorkerParameter(this.params);
        }
    }
}
