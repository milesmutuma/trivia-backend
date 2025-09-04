package com.mabawa.triviacrave.common.graphql;

import com.netflix.graphql.types.errors.TypedGraphQLError;
import graphql.GraphQLError;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.DataFetcherExceptionHandlerParameters;
import graphql.execution.DataFetcherExceptionHandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

@Component
public class GraphQlExceptionHandler implements DataFetcherExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionHandler.class);

    @Override
    public CompletableFuture<DataFetcherExceptionHandlerResult> handleException(
            DataFetcherExceptionHandlerParameters handlerParameters) {
        Throwable ex = handlerParameters.getException();
        log.error("GraphQL error: {}", ex.getMessage(), ex);

        GraphQLError error = TypedGraphQLError.newBuilder()
                .message(ex.getMessage())
                .path(handlerParameters.getPath())
                .locations(Collections.singletonList(handlerParameters.getSourceLocation()))
                .build();

        return CompletableFuture.completedFuture(
                DataFetcherExceptionHandlerResult.newResult().error(error).build()
        );
    }
}
