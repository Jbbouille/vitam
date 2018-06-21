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
package fr.gouv.vitam.ihmrecette.appserver.populate;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import fr.gouv.culture.archivesdefrance.seda.v2.IdentifierType;
import fr.gouv.culture.archivesdefrance.seda.v2.KeyType;
import fr.gouv.culture.archivesdefrance.seda.v2.KeywordsType;
import fr.gouv.culture.archivesdefrance.seda.v2.LevelType;
import fr.gouv.culture.archivesdefrance.seda.v2.TextType;
import fr.gouv.vitam.common.mapping.serializer.IdentifierTypeDeserializer;
import fr.gouv.vitam.common.mapping.serializer.IdentifierTypeSerializer;
import fr.gouv.vitam.common.mapping.serializer.KeywordTypeDeserializer;
import fr.gouv.vitam.common.mapping.serializer.KeywordTypeSerializer;
import fr.gouv.vitam.common.mapping.serializer.LevelTypeDeserializer;
import fr.gouv.vitam.common.mapping.serializer.LevelTypeSerializer;
import fr.gouv.vitam.common.mapping.serializer.TextByLangDeserializer;
import fr.gouv.vitam.common.mapping.serializer.TextByLangSerializer;
import fr.gouv.vitam.common.mapping.serializer.TextTypeDeSerializer;
import fr.gouv.vitam.common.mapping.serializer.TextTypeSerializer;
import fr.gouv.vitam.common.mapping.serializer.XMLGregorianCalendarSerializer;
import fr.gouv.vitam.common.model.unit.TextByLang;

import javax.xml.datatype.XMLGregorianCalendar;

/**
 * Unit Got Mapper
 */
public interface UnitGotMapper {

    /**
     * @return an object mapper
     */
    static ObjectMapper buildObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategy.UPPER_CAMEL_CASE);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        SimpleModule module = new SimpleModule();

        module.addSerializer(TextType.class, new TextTypeSerializer());
        module.addSerializer(LevelType.class, new LevelTypeSerializer());
        module.addSerializer(IdentifierType.class, new IdentifierTypeSerializer());
        module.addSerializer(XMLGregorianCalendar.class, new XMLGregorianCalendarSerializer());
        module.addSerializer(TextByLang.class, new TextByLangSerializer());
        module.addSerializer(KeyType.class, new KeywordTypeSerializer());

        module.addDeserializer(TextByLang.class, new TextByLangDeserializer());
        module.addDeserializer(LevelType.class, new LevelTypeDeserializer());
        module.addDeserializer(IdentifierType.class, new IdentifierTypeDeserializer());
        module.addDeserializer(TextType.class, new TextTypeDeSerializer());
        module.addDeserializer(KeyType.class, new KeywordTypeDeserializer());

        objectMapper.registerModule(module);

        return objectMapper;
    }

}
