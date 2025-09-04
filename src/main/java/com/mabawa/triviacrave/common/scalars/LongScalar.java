package com.mabawa.triviacrave.common.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.IntValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

@DgsScalar(name = "Long")
public class LongScalar implements Coercing<Long, Long> {
    @Override
    public Long serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof Long) {
            return (Long) dataFetcherResult;
        }
        throw new CoercingSerializeException("Expected a Long value.");
    }

    @Override
    public Long parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof Long) {
            return (Long) input;
        } else if (input instanceof Integer) {
            return ((Integer) input).longValue();
        } else if (input instanceof String) {
            try {
                return Long.parseLong((String) input);
            } catch (NumberFormatException e) {
                throw new CoercingParseValueException("Unable to parse value to Long.");
            }
        }
        throw new CoercingParseValueException("Expected a Long value.");
    }

    @Override
    public Long parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof IntValue) {
            return ((IntValue) input).getValue().longValue();
        }
        throw new CoercingParseLiteralException("Expected an IntValue.");
    }
}
