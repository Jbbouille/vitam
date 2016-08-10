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
package fr.gouv.vitam.workspace.rest;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.config.EncoderConfig;
import com.jayway.restassured.http.ContentType;
import fr.gouv.vitam.common.digest.Digest;
import fr.gouv.vitam.common.digest.DigestType;
import fr.gouv.vitam.common.junit.JunitHelper;
import fr.gouv.vitam.workspace.api.config.StorageConfiguration;
import fr.gouv.vitam.workspace.common.Entry;
import org.apache.commons.io.IOUtils;
import org.apache.http.entity.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import javax.ws.rs.core.Response.Status;
import java.io.*;

import static com.jayway.restassured.RestAssured.*;


public class WorkspaceResourceTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private WorkspaceApplication workspaceApplication;

    private static final String RESOURCE_URI = "/workspace/v1";

    private static final String CONTAINER_NAME = "myContainer";
    private static final String FOLDER_NAME = "myFolder";
    private static final String FOLDER_SIP = "SIP";
    private static final String OBJECT_NAME = "myObject";
    private static final String FAKE_FOLDER_NAME = "fakeFolderName";
    public static final String X_DIGEST_ALGORITHM  = "X-digest-algorithm";
    public static final String ALGO="MD5";
    public static final String X_DIGEST="X-digest";
    private static JunitHelper junitHelper;
    private static int port;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        junitHelper = new JunitHelper();
        port = junitHelper.findAvailablePort();
    }

    @AfterClass
    public static void shutdownAfterClass() {
        junitHelper.releasePort(port);
    }

    @Before
    public void setup() throws Exception {
        final StorageConfiguration configuration = new StorageConfiguration();
        final File tempDir = tempFolder.newFolder();
        configuration.setStoragePath(tempDir.getCanonicalPath());
        workspaceApplication = new WorkspaceApplication();
        WorkspaceApplication.run(configuration, port);
        RestAssured.port = port;
        RestAssured.basePath = RESOURCE_URI;
    }

    @After
    public void tearDown() throws Exception {
        workspaceApplication.stop();
    }

    // Status
    @Test
    public void givenStartedServerWhenGetStatusThenReturnStatusOk() {
        get("/status").then().statusCode(Status.OK.getStatusCode());
    }

    // Container
    @Test
    public void givenContainerAlreadyExistsWhenCreateContainerThenReturnConflict() {

        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        given().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).expect()
            .statusCode(Status.CONFLICT.getStatusCode()).when().post("/containers");
    }

    @Test
    public void givenContainerNotFoundWhenDeleteContainerThenReturnNotFound() {

        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when().delete("/containers/" + CONTAINER_NAME);

        given().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when().delete("/containers/" + CONTAINER_NAME);

    }

    @Test
    public void givenContainerNotFoundWhenGetStatusThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when().head("/containers/" + CONTAINER_NAME);
    }

    @Test
    public void givenContainerAlreadyExistsWhenCheckContainerThenReturnOk() {
        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        given().then().statusCode(Status.OK.getStatusCode()).when().head("/containers/" + CONTAINER_NAME);
    }

    @Test
    public void givenContainerNotFoundWhenCreateContainerThenReturnCreated() {

        given().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

    }

    // Folder (Directory)
    @Test
    public void givenContainerNotFoundWhenCreateFolderThenReturnNotFound() {

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

    }

    @Test
    public void givenFolderAlreadyExistsWhenCreateFolderThenReturnConflict() {

        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        with().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.CONFLICT.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

    }

    @Test
    public void givenFolderNotFoundWhenDeleteFolderThenReturnNotFound() {

        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    @Test
    public void givenContainerNotFoundWhenDeleteFolderThenReturnNotFound() {
        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderAlreadyExistsWhenDeleteFolderThenReturnNotContent() {
        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        with().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderNotFoundWhenGetFolderThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .head("/containers" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenFolderAlreadyExistsWhenCheckFolderThenReturnOk() {
        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        with().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

        given().then().statusCode(Status.OK.getStatusCode()).when()
            .head("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);

    }

    // Object
    @Test
    public void givenContainerNotFoundWhenPutObjectThenReturnNotFound() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {
            given().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.NOT_FOUND.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
        }

    }

    @Test
    public void givenContainerAlreadyExistsWhenPutObjectThenReturnCreated() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {
    
            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
    
            given().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
        }

    }

    @Test
    public void givenObjectAlreadyExistsWhenDeleteObjectThenReturnNotContent() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {

            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
    
            with().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
    
            given().then().statusCode(Status.NO_CONTENT.getStatusCode()).when()
                .delete("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }

    @Test
    public void givenObjectNotFoundWhenGetStatusThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }
    
    @Test
    public void givenObjectNotFoundWhenComputeDigestThenReturnNotFound() {
        given().header(X_DIGEST_ALGORITHM, ALGO)
        .when()
            .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
            .then().statusCode(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void givenObjectNotFoundWhenDeleteObjectThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .delete("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }

    @Test
    public void givenObjectAlreadyExistsWhenCheckObjectThenReturnOk() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {

            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
    
            with().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
    
            given().then().statusCode(Status.OK.getStatusCode()).when()
                .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }
    
    @Test
    public void givenObjectAlreadyExistsWhenComputeDigestThenReturnOk() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {
    
            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
    
            with().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
        }
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {
            Digest digest = new Digest(DigestType.fromValue(ALGO));
            digest.update(stream);
            
            given().header(X_DIGEST_ALGORITHM, ALGO)
                .when()
                .head("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME)
                .then().statusCode(Status.OK.getStatusCode()).header(X_DIGEST, digest.toString());
        }
    }

    @Test
    public void givenObjectNotFoundWhenGetObjectThenReturnNotFound() {
        given().then().statusCode(Status.NOT_FOUND.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
    }

    @Test
    public void givenObjectAlreadyExistsWhenGetObjectThenReturnOk() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {

            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
    
            with().multiPart("objectName", OBJECT_NAME).multiPart("object", OBJECT_NAME, stream).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
    
            given().then().statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/objects/" + OBJECT_NAME);
        }
    }

    // unzip
    @Test
    public void givenZipImputWhenUnzipThenReturnOK() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("sip.zip")) {

            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                    .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

            given().contentType(ContentType.BINARY).body(stream)
                    .then().statusCode(Status.CREATED.getStatusCode()).when().put("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_SIP);
        }
    }

    @Test
    public void givenContainerNotFoundWhenUnzippingObjectThenReturnNotFound() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("sip.zip")) {

            byte[] bytes = IOUtils.toByteArray(stream); //need for the test !
            given()
                    .contentType(ContentType.BINARY)
                    .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                    .content(stream).
            when()
                    .put("/containers/" + CONTAINER_NAME + "/folders/"+FOLDER_SIP).
            then()
                    .statusCode(Status.NOT_FOUND.getStatusCode());
        }
    }
    
    @Test
    public void givenFolderAlreadyExistsWhenUnzippingObjectThenReturnConflict() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("sip.zip")) {

            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
            
            with().contentType(ContentType.JSON).body(new Entry(FOLDER_SIP)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/folders");

            byte[] bytes = IOUtils.toByteArray(stream); //need for the test !
            given()
                    .contentType(ContentType.BINARY)
                    .config(RestAssured.config().encoderConfig(EncoderConfig.encoderConfig().appendDefaultContentCharsetToContentTypeIfUndefined(false)))
                    .content(stream).
            when()
                    .put("/containers/" + CONTAINER_NAME + "/folders/"+FOLDER_SIP).
            then()
                    .statusCode(Status.CONFLICT.getStatusCode());
        }
    }
    
    // uriList
    @Test
    public void givenEmptyFolderWhenfindingThenReturnNoContent() throws IOException {
        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
    }

    @Test
    public void givenNotEmptyFolderWhenfindingThenReturnOk() throws IOException {
        try (InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream("file1.pdf")) {
    
            with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
                .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");
            
          
            with().multiPart("objectName", FOLDER_NAME + "/"+OBJECT_NAME).multiPart("object", FOLDER_NAME + "/"+OBJECT_NAME, stream).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers/" + CONTAINER_NAME + "/objects");
    
            
            given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
                .statusCode(Status.OK.getStatusCode()).when()
                .get("/containers/" + CONTAINER_NAME + "/folders/" + FOLDER_NAME);
        }
    }

    
    @Test
    public void givenContainerNameFolderNameNotExistWhenfindingThenReturn_NoContent() {
       
        with().contentType(ContentType.JSON).body(new Entry(CONTAINER_NAME)).then()
            .statusCode(Status.CREATED.getStatusCode()).when().post("/containers");

        given().contentType(ContentType.JSON).body(new Entry(FOLDER_NAME)).then()
            .statusCode(Status.NO_CONTENT.getStatusCode()).when()
            .get("/containers/" + CONTAINER_NAME + "/folders/" + FAKE_FOLDER_NAME);

    }


}
