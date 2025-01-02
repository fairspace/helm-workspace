import React from 'react';
import ReactMarkdown from 'react-markdown';
import {formatDateTime, stringToBooleanValueOrNull} from '../../../common/utils/genericUtils';

import LinkedDataLink from '../LinkedDataLink';
import {BOOLEAN_URI, DATETIME_URI, MARKDOWN_URI} from '../../../constants';
import Iri from '../../../common/components/Iri';

export const ReferringValue = ({property, entry}) => {
    function renderValue(prop, value) {
        if (!value || !prop.multiLine) {
            return value;
        }
        return <span style={{whiteSpace: 'pre-line'}}>{value}</span>;
    }
    function extractDisplayValue(value) {
        switch (property.datatype) {
            case DATETIME_URI:
                return formatDateTime(value.value);
            case BOOLEAN_URI: {
                const booleanValueString = stringToBooleanValueOrNull(value.value) ? 'True' : 'False';
                return renderValue(property, booleanValueString);
            }
            case MARKDOWN_URI:
                return <ReactMarkdown>{value.value}</ReactMarkdown>;
            default:
                return value.label || renderValue(property, value.value) || <Iri iri={value.id} />;
        }
    }

    const displayValue = property.isGenericIriResource ? decodeURI(entry.id) : extractDisplayValue(entry);

    if (entry.id) {
        // External links should be represented by a direct link to the URI itself
        // Other iri entities should be opened in the metadata editor
        return property.isExternalLink ? (
            <a href={entry.id} target="_blank" rel="noreferrer">
                {entry.id}
            </a>
        ) : (
            <LinkedDataLink uri={entry.id}>{displayValue}</LinkedDataLink>
        );
    }

    return displayValue;
};

export default ReferringValue;
