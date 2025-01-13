import React from 'react';
import type {MetadataViewFilter} from './MetadataViewAPI';
import MetadataViewAPI from './MetadataViewAPI';
import useAsync from '../../common/hooks/UseAsync';
import useStateWithSessionStorage from '../../common/hooks/UseSessionStorage';
import {isNonEmptyValue} from '../../common/utils/genericUtils';

const MetadataViewContext = React.createContext({});

const SESSION_STORAGE_METADATA_FILTERS_KEY = 'FAIRSPACE_METADATA_FILTERS';

export const MetadataViewProvider = ({children, metadataViewAPI = MetadataViewAPI, sourceName = ''}) => {
    const {data = {}, error, loading, refresh} = useAsync(() => metadataViewAPI.getViews(), []);
    const [filters: MetadataViewFilter[], setFilters] = useStateWithSessionStorage(
        `${SESSION_STORAGE_METADATA_FILTERS_KEY}_${sourceName}`,
        []
    );

    const clearFilter = (facetName: string) => {
        setFilters([...filters.filter(f => f.field !== facetName)]);
    };

    const clearAllFilters = () => {
        setFilters([]);
        const queryParams = new URLSearchParams(window.location.search);
        const viewParam = queryParams.get('view');
        window.history.replaceState(null, '', `${window.location.pathname}?${viewParam ? `view=${viewParam}` : ''}`);
    };

    const updateFilters = (filterCandidates: MetadataViewFilter[]) => {
        setFilters([
            ...filters.filter(f => !filterCandidates.some(u => u.field.toLowerCase() === f.field.toLowerCase())),
            ...filterCandidates.filter(
                f =>
                    (f.values && f.values.length > 0) ||
                    isNonEmptyValue(f.min) ||
                    isNonEmptyValue(f.max) ||
                    f.prefix != null ||
                    f.booleanValue != null
            )
        ]);
    };

    return (
        <MetadataViewContext.Provider
            value={{
                views: data.views,
                filters,
                updateFilters,
                clearAllFilters,
                clearFilter,
                error,
                loading,
                refresh
            }}
        >
            {children}
        </MetadataViewContext.Provider>
    );
};

export default MetadataViewContext;
