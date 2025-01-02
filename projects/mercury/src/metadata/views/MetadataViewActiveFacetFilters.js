import React from 'react';
import {Chip, Grid, Typography} from '@mui/material';
import type {MetadataViewFacet, MetadataViewFilter} from './MetadataViewAPI';
import {ofBooleanValueType, ofRangeValueType} from './metadataViewUtils';
import {formatDate, isNonEmptyValue} from '../../common/utils/genericUtils';

type MetadataViewActiveFacetFiltersProperties = {
    facets: MetadataViewFacet[],
    filters: MetadataViewFilter[],
    setFilters: () => {}
};

export const MetadataViewActiveFacetFilters = (props: MetadataViewActiveFacetFiltersProperties) => {
    const {facets, filters, setFilters} = props;

    const renderActiveFilterValues = (facet, filter) => {
        if (ofRangeValueType(facet.type)) {
            let min;
            let max;
            let label;
            if (facet.type === 'Date') {
                min = formatDate(filter.min);
                max = formatDate(filter.max);
            } else {
                min = filter.min;
                max = filter.max;
            }
            if (isNonEmptyValue(min) && isNonEmptyValue(max)) {
                label = `${min} - ${max}`;
            } else if (isNonEmptyValue(min)) {
                label = `from: ${min}`;
            } else if (isNonEmptyValue(max)) {
                label = `to: ${max}`;
            } else {
                return <></>;
            }
            return (
                <Chip
                    className={facet.backgroundColor}
                    key={`chip-${facet.name}`}
                    label={label}
                    style={{marginLeft: 5}}
                    onDelete={() => {
                        const index = filters.indexOf(filter);
                        delete filters[index];
                        setFilters(filters);
                    }}
                />
            );
        }
        if (ofBooleanValueType(facet.type)) {
            return (
                <Chip
                    className={facet.backgroundColor}
                    key={`chip-${facet.name}`}
                    label={filter.booleanValue}
                    style={{marginLeft: 5}}
                    onDelete={() => {
                        const index = filters.indexOf(filter);
                        delete filters[index];
                        setFilters(filters);
                    }}
                />
            );
        }
        return filter.values.map(valueIri => {
            const value = facet.values.find(val => val.value.toLowerCase() === valueIri.toLowerCase());
            return (
                value && (
                    <Chip
                        className={facet.backgroundColor}
                        key={value.value}
                        label={value.label}
                        style={{marginLeft: 5}}
                        onDelete={() => {
                            const index = filters.indexOf(filter);
                            filters[index].values = [...filter.values.filter(v => v !== value.value)];
                            setFilters(filters);
                        }}
                    />
                )
            );
        });
    };

    return (
        <Grid container item direction="row" justifyContent="flex-start" alignItems="stretch" spacing={1}>
            {filters &&
                filters.map(filter => {
                    if (
                        !isNonEmptyValue(filter.min) &&
                        !isNonEmptyValue(filter.max) &&
                        (!filter.values || filter.values.length === 0) &&
                        filter.booleanValue === null
                    ) {
                        return null;
                    }
                    const facet = facets.find(f => f.name.toLowerCase() === filter.field.toLowerCase());
                    if (facet) {
                        return (
                            <Grid key={filter.field} item>
                                <Typography variant="overline" component="span">
                                    {facet.title}
                                </Typography>
                                {renderActiveFilterValues(facet, filter)}
                            </Grid>
                        );
                    }
                    return <></>;
                })}
        </Grid>
    );
};

export default MetadataViewActiveFacetFilters;
