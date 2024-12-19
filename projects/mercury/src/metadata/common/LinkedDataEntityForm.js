import React from 'react';
import PropTypes from 'prop-types';
import {List, ListItem} from '@mui/material';

import LinkedDataProperty from './LinkedDataProperty';
import {hasValue, shouldPropertyBeHidden} from './metadataUtils';
import {
    COMMENT_URI,
    CONTENT_TYPE_URI,
    CREATED_BY_URI,
    DATE_CREATED_URI,
    DATE_MODIFIED_URI,
    LABEL_URI,
    MODIFIED_BY_URI
} from '../../constants';
import LoadingInlay from '../../common/components/LoadingInlay';
import MessageDisplay from '../../common/components/MessageDisplay';
import {compareBy, comparing} from '../../common/utils/genericUtils';

type PropertyType = {
    key: string
};

const systemProperties = [CONTENT_TYPE_URI, DATE_CREATED_URI, CREATED_BY_URI, DATE_MODIFIED_URI, MODIFIED_BY_URI];

const systemPropertiesLast = compareBy(x => systemProperties.indexOf(x.key));

function labelFirst(x: PropertyType, y: PropertyType): number {
    if (x.key === y.key) {
        return 0;
    }
    if (x.key === LABEL_URI) {
        return -1;
    }
    if (y.key === LABEL_URI) {
        return 1;
    }
    return 0;
}

function descriptionFirst(x: PropertyType, y: PropertyType): number {
    if (x.key === y.key) {
        return 0;
    }
    if (x.key === COMMENT_URI) {
        return -1;
    }
    if (y.key === COMMENT_URI) {
        return 1;
    }
    return 0;
}

export const LinkedDataEntityForm = ({
    id,
    onSubmit,
    properties = [],
    values = {},
    validationErrors = {},
    errorMessage = '',
    loading = false,
    onChange = () => {},
    onAdd = () => {},
    onDelete = () => {},
    editable = true,
    typeIri
}) => {
    if (loading) {
        return <LoadingInlay />;
    }

    if (errorMessage !== '') {
        return <MessageDisplay message={errorMessage} />;
    }

    return (
        <form
            id={id}
            onSubmit={e => {
                e.preventDefault();
                e.stopPropagation();
                if (!editable) {
                    onSubmit();
                }
            }}
            noValidate
        >
            <List>
                {properties
                    // Some properties are always hidden (e.g. @type) or hidden based on the type of entity (e.g. label for collection)
                    // Properties are also hidden when it is not editable and there is no value
                    .filter(p => !shouldPropertyBeHidden(p.key, typeIri) && (p.isEditable || hasValue(values[p.key])))

                    // Properties are sorted based on the sh:order property, or by its label otherwise
                    .sort(
                        comparing(
                            labelFirst,
                            descriptionFirst,
                            systemPropertiesLast,
                            compareBy(p => (Number.isNaN(Number(p.order)) ? Number.MAX_SAFE_INTEGER : Number(p.order))),
                            compareBy('label')
                        )
                    )
                    .map(p => (
                        <ListItem key={p.key} disableGutters style={{display: 'block'}}>
                            <LinkedDataProperty
                                formEditable={editable}
                                property={p}
                                values={values[p.key]}
                                validationErrors={validationErrors[p.key]}
                                onAdd={editable ? value => onAdd(p, value) : () => {}}
                                onChange={editable ? (value, index) => onChange(p, value, index) : () => {}}
                                onDelete={editable ? index => onDelete(p, index) : () => {}}
                            />
                        </ListItem>
                    ))}
            </List>
        </form>
    );
};

LinkedDataEntityForm.propTypes = {
    onAdd: PropTypes.func,
    onChange: PropTypes.func,
    onDelete: PropTypes.func,

    errorMessage: PropTypes.string,
    typeIri: PropTypes.string,

    loading: PropTypes.bool,
    properties: PropTypes.array,
    values: PropTypes.object,
    validationErrors: PropTypes.object,
    editable: PropTypes.bool
};

export default LinkedDataEntityForm;
