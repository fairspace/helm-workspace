import React from 'react';

import {ReferringValue} from '../ReferringValue';
import LinkedDataLink from '../../LinkedDataLink';
import {BOOLEAN_URI} from '../../../../constants';

describe('ReferringValue', () => {
    it('should render an external link directly', () => {
        const property = {
            isGenericIriResource: true,
            isExternalLink: true
        };
        const entry = {
            id: 'https://thehyve.nl'
        };
        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual(
            <a rel="noreferrer" target="_blank" href="https://thehyve.nl">
                https://thehyve.nl
            </a>
        );
    });

    it('should render a generic iri resource as link to editor', () => {
        const property = {
            isGenericIriResource: true,
            isExternalLink: false
        };
        const entry = {
            id: 'https://thehyve.nl'
        };

        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual(<LinkedDataLink uri="https://thehyve.nl">https://thehyve.nl</LinkedDataLink>);
    });

    it('should render a regular links with the label of the resource', () => {
        const property = {};
        const entry = {
            id: 'https://my-resource',
            label: 'My resource'
        };

        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual(<LinkedDataLink uri="https://my-resource">My resource</LinkedDataLink>);
    });

    it('should render a values without URI as its label', () => {
        const property = {};
        const entry = {
            label: 'My resource'
        };

        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual('My resource');
    });

    it('should render a "true" value', () => {
        const property = {datatype: BOOLEAN_URI};
        const entry = {
            label: '',
            value: 'true'
        };

        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual('True');
    });

    it('should render a "false" value', () => {
        const property = {datatype: BOOLEAN_URI};
        const entry = {
            label: '',
            value: 'false'
        };

        expect(
            ReferringValue({
                property,
                entry
            })
        ).toEqual('False');
    });
});
