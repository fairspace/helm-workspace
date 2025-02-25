import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import React from 'react';
import '@testing-library/jest-dom/extend-expect';
import {ThemeProvider} from '@mui/material/styles';
import {MetadataViewTable} from '../MetadataViewTable';
// eslint-disable-next-line jest/no-mocks-import
import {mockRows, mockViews} from '../__mocks__/MetadataViewAPI';
import {RESOURCES_VIEW} from '../metadataViewUtils';
import theme from '../../../App.theme';

describe('MetadataViewTable', () => {
    const historyMock = {
        push: jest.fn()
    };

    it('renders correct header and values columns', () => {
        const view = 'Sample';
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        const {queryByText, queryAllByText} = render(
            <ThemeProvider theme={theme}>
                <MetadataViewTable
                    columns={columns}
                    visibleColumnNames={columns.map(c => c.name)}
                    data={data}
                    locationContext=""
                    toggleRow={() => {}}
                    history={historyMock}
                    idColumn={columns[0]}
                    collections={[]}
                    textFiltersObject={{}}
                    setTextFiltersObject={() => {}}
                    loading={false}
                    checkboxes={{}}
                    setCheckboxState={() => {}}
                />
            </ThemeProvider>
        );

        expect(queryByText('Sample')).toBeInTheDocument();
        expect(queryByText('Sample type')).toBeInTheDocument();
        expect(queryByText('Topography')).toBeInTheDocument();
        expect(queryByText('Nature')).toBeInTheDocument();
        expect(queryByText('Origin')).toBeInTheDocument();
        expect(queryByText('Files')).toBeInTheDocument();
        expect(queryByText('S01')).toBeInTheDocument();
        expect(queryByText('S02')).toBeInTheDocument();
        expect(queryAllByText('Tissue').length).toBe(2);
        expect(queryAllByText('DNA').length).toBe(2);
        expect(queryByText('Lip')).toBeInTheDocument();
        expect(queryByText('Tongue')).toBeInTheDocument();
        expect(queryByText('Is reused')).toBeInTheDocument();
        // check if the icons for boolean values are rendered
        expect(screen.getByTestId('icon-true')).toBeInTheDocument();
        expect(screen.getByTestId('icon-false')).toBeInTheDocument();
    });

    it('renders visible columns only', () => {
        const view = 'Sample';
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        const {queryByText, queryAllByText} = render(
            <ThemeProvider theme={theme}>
                <MetadataViewTable
                    columns={columns}
                    visibleColumnNames={['Sample', 'Sample_sampleType', 'Sample_origin']}
                    data={data}
                    view=""
                    locationContext=""
                    toggleRow={() => {}}
                    history={historyMock}
                    idColumn={columns[0]}
                    collections={[]}
                    textFiltersObject={{}}
                    setTextFiltersObject={() => {}}
                    checkboxes={{}}
                    setCheckboxState={() => {}}
                />
            </ThemeProvider>
        );

        expect(queryByText('Sample')).toBeInTheDocument();
        expect(queryByText('Sample type')).toBeInTheDocument();
        expect(queryByText('Lip')).not.toBeInTheDocument();
        expect(queryByText('S01')).toBeInTheDocument();
        expect(queryByText('S02')).toBeInTheDocument();
        expect(queryAllByText('Tissue').length).toBe(2);
        expect(queryByText('Topography')).not.toBeInTheDocument();
        expect(queryByText('Nature')).not.toBeInTheDocument();
        expect(queryByText('Origin')).toBeInTheDocument();
        expect(queryByText('Files')).not.toBeInTheDocument();
        expect(queryByText('DNA')).not.toBeInTheDocument();
        expect(queryByText('Tongue')).not.toBeInTheDocument();
        expect(queryByText('Is reused')).not.toBeInTheDocument();
        // check if the icons for boolean values are not rendered
        expect(screen.queryByTestId('icon-true')).not.toBeInTheDocument();
        expect(screen.queryByTestId('icon-false')).not.toBeInTheDocument();
    });

    it('should redirect when opening collection entry', async () => {
        const user = userEvent.setup();
        const view = RESOURCES_VIEW;
        const {columns} = mockViews().find(v => v.name === view);
        const data = {rows: mockRows(view)};
        render(
            <ThemeProvider theme={theme}>
                <MetadataViewTable
                    view={view}
                    columns={columns}
                    visibleColumnNames={columns.map(c => c.name)}
                    data={data}
                    collections={[{iri: 'http://localhost:8080/api/webdav/c01', access: 'Read'}]}
                    locationContext=""
                    toggleRow={() => {}}
                    history={historyMock}
                    idColumn={columns[0]}
                    loading={false}
                    textFiltersObject={{}}
                    setTextFiltersObject={() => {}}
                    checkboxes={{}}
                    setCheckboxState={() => {}}
                />
            </ThemeProvider>
        );

        const tableRows = screen.queryAllByRole('row');
        expect(tableRows.length).toEqual(2);

        await user.dblClick(tableRows[1]);
        expect(historyMock.push).toHaveBeenCalledTimes(1);
        expect(historyMock.push).toHaveBeenCalledWith('/collections/c01');
    });
});
