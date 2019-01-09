import {createErrorHandlingPromiseAction, dispatchIfNeeded} from "../utils/redux";
import MetadataAPI, {TYPE_URI} from "../services/MetadataAPI/MetadataAPI";
import {
    METADATA,
    METADATA_ALL_ENTITIES,
    METADATA_COMBINATION,
    METADATA_ENTITIES,
    METADATA_NEW_ENTITY,
    METADATA_URI_BY_PATH,
    METADATA_VOCABULARY,
    UPDATE_METADATA
} from "./actionTypes";
import * as actionTypes from "../utils/redux-action-types";
import {getSingleValue} from "../utils/metadatautils";

export const invalidateMetadata = subject => ({
    type: actionTypes.invalidate(METADATA),
    meta: {subject}
});

export const updateMetadata = (subject, predicate, values) => ({
    type: UPDATE_METADATA,
    payload: MetadataAPI.update(subject, predicate, values),
    meta: {
        subject,
        predicate,
        values
    }
});

export const createMetadataEntity = (type, id) => {
    let infix = getSingleValue(type, 'http://fairspace.io/ontology#classInfix');
    if (!infix) {
        console.error(`Couldn't determine a class infix for ${type['@id']}`);
        infix = 'generic';
    }
    const subject = `${window.location.origin}/iri/${infix}/${id}`;
    return {
        type: METADATA_NEW_ENTITY,
        payload: MetadataAPI.get({subject})
            .then((meta) => {
                if (meta.length) {
                    throw Error(`Metadata entity already exists: ${subject}`);
                }
            })
            .then(() => MetadataAPI.update(subject, TYPE_URI, [{id: type['@id']}]))
            .then(() => subject),
        meta: {
            subject,
            type: type['@id']
        }
    };
};

const fetchJsonLdBySubject = createErrorHandlingPromiseAction(subject => ({
    type: METADATA,
    payload: MetadataAPI.get({subject}),
    meta: {
        subject
    }
}));

const fetchVocabulary = createErrorHandlingPromiseAction(() => ({
    type: METADATA_VOCABULARY,
    payload: MetadataAPI.getVocabulary()
}));

export const fetchMetadataVocabularyIfNeeded = () => dispatchIfNeeded(
    fetchVocabulary,
    state => (state && state.cache ? state.cache.vocabulary : undefined)
);

export const fetchJsonLdBySubjectIfNeeded = subject => dispatchIfNeeded(
    () => fetchJsonLdBySubject(subject),
    state => (state && state.cache && state.cache.jsonLdBySubject ? state.cache.jsonLdBySubject[subject] : undefined)
);

const combineMetadataForSubject = createErrorHandlingPromiseAction((subject, dispatch) => ({
    type: METADATA_COMBINATION,
    payload: Promise.all([
        dispatch(fetchJsonLdBySubjectIfNeeded(subject)),
        dispatch(fetchMetadataVocabularyIfNeeded())
    ]).then(([jsonLd, vocabulary]) => vocabulary.value.combine(jsonLd.value, subject)),
    meta: {subject}
}));

const fetchEntitiesByType = createErrorHandlingPromiseAction(type => ({
    type: METADATA_ENTITIES,
    payload: MetadataAPI.getEntitiesByType(type),
    meta: {
        type
    }
}));

const fetchAllEntities = createErrorHandlingPromiseAction(dispatch => ({
    type: METADATA_ALL_ENTITIES,
    payload: dispatch(fetchMetadataVocabularyIfNeeded())
        .then(({value: vocabulary}) => MetadataAPI.getEntitiesByTypes(
            vocabulary.getFairspaceClasses()
                .map(entry => entry['@id'])
        ))
}));

export const fetchCombinedMetadataIfNeeded = subject => dispatchIfNeeded(
    () => combineMetadataForSubject(subject),
    state => (state && state.metadataBySubject ? state.metadataBySubject[subject] : undefined)
);

export const fetchEntitiesIfNeeded = type => dispatchIfNeeded(
    () => fetchEntitiesByType(type),
    state => (state && state.cache && state.cache.entitiesByType ? state.cache.entitiesByType[type] : undefined)
);

export const fetchAllEntitiesIfNeeded = () => dispatchIfNeeded(
    () => fetchAllEntities(),
    state => (state && state.cache ? state.cache.allEntities : undefined)
);

const getUriByPath = createErrorHandlingPromiseAction(path => ({
    type: METADATA_URI_BY_PATH,
    payload: MetadataAPI.getSubjectByPath(path),
    meta: {path}
}));

export const fetchSubjectByPathIfNeeded = path => dispatchIfNeeded(
    () => getUriByPath(path),
    state => state && state.cache && state.cache.subjectByPath && state.cache.subjectByPath[path]
);
