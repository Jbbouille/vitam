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
package fr.gouv.vitam.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.function.Supplier;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;

import fr.gouv.vitam.api.exception.MetaDataAlreadyExistException;
import fr.gouv.vitam.api.exception.MetaDataDocumentSizeException;
import fr.gouv.vitam.api.exception.MetaDataExecutionException;
import fr.gouv.vitam.api.exception.MetaDataNotFoundException;
import fr.gouv.vitam.common.exception.InvalidParseOperationException;

public class InsertMetaDataClientTest extends JerseyTest {
    private static final String QUERY = "";
    private static final String url = "http://localhost:8082";
    private static final MetaDataClient client = new MetaDataClient(url);

    Supplier<Response> mock;

    @Override
    protected Application configure() {
        enable(TestProperties.LOG_TRAFFIC);
        enable(TestProperties.DUMP_ENTITY);
        forceSet(TestProperties.CONTAINER_PORT, "8082");
        mock = mock(Supplier.class);
        return new ResourceConfig().registerInstances(new MyUnitsResource(mock));
    }

    @Path("/metadata/v1")
    public static class MyUnitsResource {
        private final Supplier<Response> expectedResponse;

        public MyUnitsResource(Supplier<Response> expectedResponse) {
            this.expectedResponse = expectedResponse;
        }

        @Path("units")
        @POST
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        public Response insertUnit(String insertRequest) {
            return expectedResponse.get();
        }
    }

    @Test(expected = MetaDataNotFoundException.class)
    public void givenParentNotFoundRequestWhenInsertThenReturnNotFound() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.NOT_FOUND).build());
        client.insert(QUERY);
    }

    @Test(expected = MetaDataAlreadyExistException.class)
    public void givenUnitAlreadyExistsWhenInsertThenReturnConflict() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.CONFLICT).build());
        client.insert(QUERY);
    }

    @Test(expected = MetaDataDocumentSizeException.class)
    public void givenEntityTooLargeRequestWhenInsertThenReturnRequestEntityTooLarge() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.REQUEST_ENTITY_TOO_LARGE).build());
        client.insert(QUERY);
    }

    @Test(expected = MetaDataExecutionException.class)
    public void shouldRaiseExceptionWhenExecution() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.INTERNAL_SERVER_ERROR).build());
        client.insert(QUERY);
    }

    @Test(expected = InvalidParseOperationException.class)
    public void givenInvalidRequestWhenInsertThenReturnBadRequest() throws Exception {
        when(mock.get()).thenReturn(Response.status(Status.BAD_REQUEST).build());
        client.insert(QUERY);
    }
}
