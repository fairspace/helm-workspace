import React, {useCallback} from 'react';
import {Checkbox, Link, Table, TableBody, TableCell, TableHead, TableRow} from '@mui/material';
import {Check, Close} from '@mui/icons-material';
import makeStyles from '@mui/styles/makeStyles';
import {Link as RouterLink} from 'react-router-dom';
import useDeepCompareEffect from 'use-deep-compare-effect';
import type {MetadataViewColumn, MetadataViewData} from './MetadataViewAPI';
import {TextualValueTypes} from './MetadataViewAPI';
import type {MetadataViewEntity, MetadataViewEntityWithLinkedFiles} from './metadataViewUtils';
import {RESOURCES_VIEW} from './metadataViewUtils';
import {formatDate, stringToBooleanValueOrNull} from '../../common/utils/genericUtils';
import type {Collection} from '../../collections/CollectionAPI';
import {collectionAccessIcon} from '../../collections/collectionUtils';
import {getPathFromIri, redirectLink} from '../../file/fileUtils';
import ColumnFilterInput from '../../common/components/ColumnFilterInput';

type MetadataViewTableProperties = {
    data: MetadataViewData,
    loading: boolean,
    columns: MetadataViewColumn[],
    visibleColumnNames: string[],
    idColumn: MetadataViewColumn,
    toggleRow: () => {},
    history: any,
    selected?: MetadataViewEntityWithLinkedFiles,
    view: string,
    collections: Collection[],
    textFiltersObject: Object,
    setTextFiltersObject: () => {}
};

const useStyles = makeStyles(() => ({
    headerCellContents: {
        verticalAlign: 'top',
        whiteSpace: 'nowrap'
    },
    cellContents: {
        verticalAlign: 'top',
        display: 'block',
        whiteSpace: 'nowrap',
        maxWidth: '40em',
        overflow: 'hidden',
        overflowWrap: 'break-word',
        textOverflow: 'ellipsis'
    }
}));

const CUSTOM_RESOURCE_COLUMNS = ['access', 'path'];
const RESOURCE_TYPE_COLUMN = `${RESOURCES_VIEW}_type`;

export const MetadataViewTable = (props: MetadataViewTableProperties) => {
    const {columns, visibleColumnNames, loading, data, toggleRow, selected, view, idColumn, history, collections} =
        props;
    const {textFiltersObject, setTextFiltersObject} = props;
    const {checkboxes, setCheckboxState} = props;
    const classes = useStyles();
    const visibleColumns = columns.filter(column => visibleColumnNames.includes(column.name));
    const dataLinkColumn = columns.find(c => c.type === 'dataLink');
    const isResourcesView = view === RESOURCES_VIEW;

    const isCustomResourceColumn = (column: MetadataViewColumn) =>
        isResourcesView && CUSTOM_RESOURCE_COLUMNS.includes(column.name) && column.type === 'Custom';

    const getAccess = (iri: string) => {
        const col = collections.find(c => c.iri === iri || iri.startsWith(c.iri + '/'));
        return col ? col.access : 'None';
    };

    const getResourceType = (row: Map<string, any>) =>
        row[RESOURCE_TYPE_COLUMN] && row[RESOURCE_TYPE_COLUMN][0] && row[RESOURCE_TYPE_COLUMN][0].value;

    const handleResultSingleClick = (iri: string, label: string, linkedFiles: MetadataViewEntity[]) => {
        if (selected && selected.iri === iri) {
            toggleRow();
        } else {
            toggleRow({label, iri, linkedFiles: linkedFiles || []});
        }
    };

    const initializeCheckboxes = useCallback(() => {
        if (idColumn && data && data.rows) {
            data.rows.forEach(row => {
                const key = row[idColumn.name][0].value;
                if (checkboxes[key] === undefined) {
                    setCheckboxState(key, false);
                }
            });
        }
    }, [checkboxes, data, idColumn, setCheckboxState]);

    const handleCheckallChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        if (event.target) {
            Array.from(Object.keys(checkboxes)).forEach(key => {
                setCheckboxState(key, event.target.checked);
            });
        }
    };

    const handleCheckboxChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        setCheckboxState(event.target.id, event.target.checked);
    };

    const handleResultDoubleClick = (iri: string, row: Map<string, any>) => {
        if (isResourcesView) {
            history.push(redirectLink(iri, getResourceType(row)));
        }
    };

    const renderCustomResourceColumn = (row: Map<string, any>, column: MetadataViewColumn) => {
        const iri = row[idColumn.name][0].value;
        switch (column.name) {
            case 'access': {
                const access = getAccess(iri);
                return <TableCell key={column.name}>{collectionAccessIcon(access)}</TableCell>;
            }
            case 'path': {
                const path = getPathFromIri(iri);
                const type = getResourceType(row);
                return (
                    <TableCell key={column.name}>
                        <Link
                            to={redirectLink(iri, type)}
                            component={RouterLink}
                            color="inherit"
                            underline="hover"
                            className={classes.cellContents}
                        >
                            {path}
                        </Link>
                    </TableCell>
                );
            }
            default:
                return <TableCell />;
        }
    };

    const renderColumnFilter = (columnName: string) => {
        const filterValue = textFiltersObject[columnName];
        const setFilterValue = value => setTextFiltersObject({...textFiltersObject, [columnName]: value});
        return (
            <ColumnFilterInput
                placeholder="Filter"
                filterValue={filterValue}
                setFilterValue={setFilterValue}
                useApplyButton
            />
        );
    };

    const renderBooleanIcon = (displayValue: string) => {
        const value = stringToBooleanValueOrNull(displayValue);
        if (value === true) {
            return <Check size="small" data-testid="icon-true" />;
        }
        if (value === false) {
            return <Close size="small" data-testid="icon-false" />;
        }
        return '';
    };

    const renderTableCell = (row: Map<string, any>, column: MetadataViewColumn, onClickHandler) => {
        if (isCustomResourceColumn(column)) {
            return renderCustomResourceColumn(row, column);
        }

        const value = row[column.name];
        const displayValue = (value || [])
            .map(v => (column.type === 'Date' ? formatDate(v.value) : v.label))
            .join(', ');

        if (column.type === 'Boolean') {
            return <TableCell key={column.name}>{renderBooleanIcon(displayValue)}</TableCell>;
        }

        return (
            <TableCell key={column.name} onClick={onClickHandler}>
                <span className={classes.cellContents}>{displayValue}</span>
            </TableCell>
        );
    };

    useDeepCompareEffect(() => {
        initializeCheckboxes();
    }, [data, initializeCheckboxes]);

    const checkedCount = Object.values(checkboxes)
        ? Object.values(checkboxes).reduce((sum, item) => (item === true ? sum + 1 : sum), 0)
        : 0;
    const rowCount = data && data.rows && data.rows.length;

    return (
        <Table data-testid="results-table" size="small" stickyHeader={!loading}>
            <TableHead className={classes.headerRow}>
                <TableRow sx={{'&:last-child td, &:last-child th': {border: 0}}}>
                    <TableCell style={{padding: 0}}>
                        <Checkbox
                            id="checkAll"
                            key={'checkall-key-' + checkedCount}
                            onChange={handleCheckallChange}
                            defaultChecked={rowCount > 0 && checkedCount === rowCount}
                        />
                    </TableCell>
                    {visibleColumns.map(column => (
                        <TableCell key={column.name} className={classes.headerCellContents}>
                            {column.title}
                            {TextualValueTypes.includes(column.type) && renderColumnFilter(column.name)}
                        </TableCell>
                    ))}
                </TableRow>
            </TableHead>
            <TableBody>
                {idColumn &&
                    data &&
                    data.rows &&
                    data.rows.map(row => (
                        <TableRow
                            className={classes.row}
                            key={row[idColumn.name][0].value}
                            hover
                            selected={selected && selected.iri === row[idColumn.name][0].value}
                            onDoubleClick={() => handleResultDoubleClick(row[idColumn.name][0].value, row)}
                        >
                            <TableCell style={{padding: 0}}>
                                <Checkbox
                                    id={row[idColumn.name][0].value}
                                    key={Math.random()}
                                    defaultChecked={checkboxes[row[idColumn.name][0].value]}
                                    onChange={handleCheckboxChange}
                                />
                            </TableCell>
                            {visibleColumns.map(column =>
                                renderTableCell(row, column, () =>
                                    handleResultSingleClick(
                                        row[idColumn.name][0].value,
                                        row[idColumn.name][0].label,
                                        dataLinkColumn ? row[dataLinkColumn.name] : []
                                    )
                                )
                            )}
                        </TableRow>
                    ))}
            </TableBody>
        </Table>
    );
};

export default MetadataViewTable;
