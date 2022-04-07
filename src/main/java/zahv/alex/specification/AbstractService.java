package zahv.alex.specification;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static jdk.internal.joptsimple.internal.Strings.isNullOrEmpty;

/**
 * @author azakhvalinskiy
 * @date 19.07.17
 */

@Slf4j
public abstract class AbstractService<E> {

    protected <T> CommonFilter<E> getFilter(T t) {
        return ofNullable(t)
                .map(obj -> {
                    CommonFilter<E> filter = new CommonFilter<>();
                    filter.setConditions(getCommonFilterConditions(obj));
                    return filter;
                })
                .orElse(null);
    }

    protected <T> List<Condition> getCommonFilterConditions(T t) {
        Field[] fields = t.getClass().getDeclaredFields();
        return Stream.of(fields)
                .map(field -> {
                    try {
                        return getCondition(t, field);
                    } catch (IllegalAccessException e) {
                        log.error("Could not get condition", e);
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(toList());
    }

    private <T> Condition getCondition(T t, Field field) throws IllegalAccessException {
        field.setAccessible(true);
        Object fieldValue = field.get(t);
        if (fieldValue == null || (fieldValue instanceof String && fieldValue.equals(""))) {
            return null;
        }

        FilterOptions filterOptions = field.getAnnotation(FilterOptions.class);
        ComparisonEnum comparisonOperation = ofNullable(filterOptions).map(FilterOptions::operation)
                .orElse(ComparisonEnum.EQ);

        if (fieldValue instanceof String) {
            return new Condition(
                    ofNullable(filterOptions).map(FilterOptions::operation).orElse(ComparisonEnum.LIKE),
                    getFiledName(field, filterOptions),
                    getStringValue((String) fieldValue, filterOptions.searchPattern())
            );

        } else if (fieldValue instanceof Date) {
            return new Condition(comparisonOperation, getFiledName(field, filterOptions), (Date) fieldValue);

        } else if (fieldValue instanceof LocalDate) {
            Boolean convertDateToDateTime
                    = ofNullable(filterOptions).map(FilterOptions::convertDateToDateTime).orElse(false);

            LocalDate dateValue = (LocalDate) fieldValue;

            return convertDateToDateTime
                    ? new Condition(comparisonOperation, getFiledName(field, filterOptions), dateValue.atStartOfDay())
                    : new Condition(comparisonOperation, getFiledName(field, filterOptions), (LocalDate) fieldValue);

        } else if (fieldValue instanceof Boolean) {
            return new Condition(comparisonOperation, getFiledName(field, filterOptions), (Boolean) fieldValue);

        }
        return new Condition(comparisonOperation, getFiledName(field, filterOptions), fieldValue.toString());
    }

    private String getFiledName(Field field, FilterOptions filterOptions) {
        return isNullOrEmpty(ofNullable(filterOptions).map(FilterOptions::fieldName).orElse(""))
                ? field.getName()
                : filterOptions.fieldName();
    }

    private String getStringValue(String value, SearchPattern searchPattern) {
        return ofNullable(searchPattern)
                .map(p -> String.format(p.getTemplate(), value))
                .orElse(String.format(SearchPattern.LEFT_RIGHT.getTemplate(), value));
    }

}
