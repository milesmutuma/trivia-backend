package com.mabawa.triviacrave.common.scalars;

import com.netflix.graphql.dgs.DgsScalar;
import graphql.language.StringValue;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

@DgsScalar(name = "LocalDateTime")
public class LocalDateTimeScalar implements Coercing<LocalDateTime, String> {
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    
    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        if (dataFetcherResult instanceof LocalDateTime) {
            return ((LocalDateTime) dataFetcherResult).format(FORMATTER);
        }
        throw new CoercingSerializeException("Expected a LocalDateTime value.");
    }

    @Override
    public LocalDateTime parseValue(Object input) throws CoercingParseValueException {
        if (input instanceof String) {
            try {
                return LocalDateTime.parse((String) input, FORMATTER);
            } catch (DateTimeParseException e) {
                throw new CoercingParseValueException("Unable to parse value to LocalDateTime: " + input);
            }
        } else if (input instanceof LocalDateTime) {
            return (LocalDateTime) input;
        }
        throw new CoercingParseValueException("Expected a String or LocalDateTime value.");
    }

    @Override
    public LocalDateTime parseLiteral(Object input) throws CoercingParseLiteralException {
        if (input instanceof StringValue) {
            try {
                return LocalDateTime.parse(((StringValue) input).getValue(), FORMATTER);
            } catch (DateTimeParseException e) {
                throw new CoercingParseLiteralException("Unable to parse literal to LocalDateTime: " + input);
            }
        }
        throw new CoercingParseLiteralException("Expected a StringValue.");
    }
}