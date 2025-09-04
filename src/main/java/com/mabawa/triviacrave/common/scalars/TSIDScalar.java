package com.mabawa.triviacrave.common.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.springframework.stereotype.Component;

@DgsScalar(name = "TSID")
@Component
public class TSIDScalar implements Coercing<Long, String> {

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult == null) return null;
        if (dataFetcherResult instanceof Long) {
            return io.hypersistence.tsid.TSID.from((Long) dataFetcherResult).toString();
        }
        throw new CoercingSerializeException("Expected a Long object.");
    }

    @Override
    public Long parseValue(Object input) throws CoercingParseValueException {
        if (input == null) return null;
        if (input instanceof String) {
            return io.hypersistence.tsid.TSID.from((String) input).toLong();
        }
        throw new CoercingParseValueException("Expected a String object.");
    }

    @Override
    public Long parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input == null) return null;
        if (input instanceof StringValue) {
            return io.hypersistence.tsid.TSID.from(((StringValue) input).getValue()).toLong();
        }
        throw new CoercingParseLiteralException("Expected a StringValue.");
    }
}
