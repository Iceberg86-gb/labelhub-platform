import type { components } from '../../shared/api/generated/schema';

type ApiFieldError = components['schemas']['ApiFieldError'];

export type FieldErrorMap = Map<string, string[]>;

/**
 * Submit 422 uses ApiFieldError.field as the dynamic SchemaField stableId.
 * Other ApiError producers may use OpenAPI request property names instead.
 */
export function fieldErrorsToStableIdMap(fieldErrors: ApiFieldError[]): FieldErrorMap {
  const map = new Map<string, string[]>();
  fieldErrors.forEach((fieldError) => {
    const messages = map.get(fieldError.field) ?? [];
    messages.push(fieldError.message);
    map.set(fieldError.field, messages);
  });
  return map;
}

export function selectVisibleFieldErrors(clientErrors: FieldErrorMap, serverErrors: FieldErrorMap | null): FieldErrorMap {
  return serverErrors ?? clientErrors;
}
