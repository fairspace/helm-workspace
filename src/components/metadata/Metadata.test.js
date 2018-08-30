import Metadata from "./Metadata"
import React from 'react';
import {mount} from "enzyme";

function flushPromises() {
    return new Promise(resolve => setImmediate(resolve));
}

let mockMetadataStore;

beforeEach(() => {
    mockMetadataStore = {
        getVocabulary: jest.fn(() => Promise.resolve(vocabulary)),
        get: jest.fn(() => Promise.resolve(metadata))
    }
});

it('shows result when subject provided', () => {
    const wrapper = mount(<Metadata subject={"http://fairspace.com/iri/collections/1"} metadataStore={mockMetadataStore} />);
    return flushPromises().then(() => {
        wrapper.update();
    }).then(() => {
        const result = wrapper.find("li");
        expect(result.length).toEqual(4);
    });
});

const metadata = {
    "@id": "http://fairspace.com/iri/collections/1",
    "@type": "http://fairspace.io/ontology#Collection",
    "description": "My first collection",
    "name": "Collection 5",
    "@context": {
        "name": {
            "@id": "http://fairspace.io/ontology#name"
        },
        "description": {
            "@id": "http://fairspace.io/ontology#description"
        }
    }
};

const vocabulary = {
    "@context": {
        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
        "dc": "http://purl.org/dc/elements/1.1/",
        "schema": "http://schema.org/",
        "fairspace": "http://fairspace.io/ontology#"
    },
    "@graph": [
        {
            "@id": "fairspace:name",
            "@type": "rdf:Property",
            "rdfs:label": "Name"
        },
        {
            "@id": "fairspace:description",
            "@type": "rdf:Property",
            "rdfs:label": "Description"
        },
        {
            "@id": "fairspace:Collection",
            "@type": "rdf:Class",
            "rdfs:label": "Collection"
        }
    ]
};
