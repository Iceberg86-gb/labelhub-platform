import type { SchemaDocument } from '../../entities/schema/schemaTypes';
import { useSchemaQuery } from './useSchemaQuery';
import { useSchemaVersionQuery } from './useSchemaVersionQuery';

const EMPTY_SCHEMA_DOCUMENT: SchemaDocument = { fields: [] };

export function useSchemaCurrentVersionQuery(schemaId: number) {
  const schemaQuery = useSchemaQuery(schemaId);
  const currentVersionId = schemaQuery.data?.currentVersionId ?? null;
  const currentVersionQuery = useSchemaVersionQuery(schemaId, currentVersionId);
  const shouldLoadVersion = Boolean(currentVersionId);

  return {
    schemaQuery,
    currentVersionQuery,
    schema: schemaQuery.data,
    version: currentVersionQuery.data ?? null,
    document: shouldLoadVersion
      ? currentVersionQuery.data?.schemaJson
      : EMPTY_SCHEMA_DOCUMENT,
    isLoading: schemaQuery.isLoading || (shouldLoadVersion && currentVersionQuery.isLoading),
    isFetching: schemaQuery.isFetching || currentVersionQuery.isFetching,
    isError: schemaQuery.isError || currentVersionQuery.isError,
    error: schemaQuery.error ?? currentVersionQuery.error,
    hasCurrentVersion: shouldLoadVersion,
    refetch: async () => {
      await schemaQuery.refetch();
      if (shouldLoadVersion) {
        await currentVersionQuery.refetch();
      }
    },
  };
}

