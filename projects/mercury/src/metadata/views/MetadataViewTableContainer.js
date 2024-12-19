import React, {useContext, useEffect, useState} from 'react';
import {
    CircularProgress,
    FormControlLabel,
    IconButton,
    Box,
    TableContainer,
    TablePagination,
    Tooltip,
    Typography
} from '@mui/material';
import withStyles from '@mui/styles/withStyles';
import {useHistory} from 'react-router-dom';
import {Addchart, ViewColumn, Check, ErrorOutline} from '@mui/icons-material';
import Checkbox from '@mui/material/Checkbox';
import FormControl from '@mui/material/FormControl';
import Popover from '@mui/material/Popover';
import CheckBoxOutlineBlankIcon from '@mui/icons-material/CheckBoxOutlineBlank';
import CheckBoxIcon from '@mui/icons-material/CheckBox';
import GetAppIcon from '@mui/icons-material/GetApp';
import FormGroup from '@mui/material/FormGroup';
import useDeepCompareEffect, {useDeepCompareEffectNoCheck} from 'use-deep-compare-effect';
import {useTheme} from '@mui/material/styles';

import type {MetadataViewColumn, MetadataViewFilter} from './MetadataViewAPI';
import MessageDisplay from '../../common/components/MessageDisplay';
import type {MetadataViewEntityWithLinkedFiles} from './metadataViewUtils';
import useViewData from './UseViewData';
import MetadataViewTable from './MetadataViewTable';
import useStateWithLocalStorage from '../../common/hooks/UseLocalStorage';
import {Collection} from '../../collections/CollectionAPI';
import LoadingOverlayWrapper from '../../common/components/LoadingOverlayWrapper';
import {isNonEmptyValue} from '../../common/utils/genericUtils';
import MetadataViewActiveTextFilters from './MetadataViewActiveTextFilters';
import TablePaginationActions from '../../common/components/TablePaginationActions';
import FeaturesContext from '../../common/contexts/FeaturesContext';
import ProgressButton from '../../common/components/ProgressButton';
import {ANALYSIS_EXPORT_SUBPATH, ExtraLocalStorage} from '../../file/FileAPI';
import ErrorDialog from '../../common/components/ErrorDialog';

type MetadataViewTableContainerProperties = {
    columns: MetadataViewColumn[],
    idColumn: MetadataViewColumn,
    filters: MetadataViewFilter[],
    textFiltersObject: Object,
    setTextFiltersObject: () => {},
    toggleRow: () => {},
    view: string,
    viewCountDisplayLimit: number,
    collections: Collection[],
    locationContext: string,
    selected: MetadataViewEntityWithLinkedFiles,
    hasInactiveFilters: boolean,
    classes: any
};

const styles = theme => ({
    footerButtonDiv: {
        display: 'flex',
        padding: 0,
        margin: 4
    },
    footerCountDiv: {
        marginTop: 10,
        marginLeft: 7
    },
    exportButton: {
        margin: 3,
        fontSize: 12,
        padding: 2
    },
    countErrorIcon: {
        fontSize: 16,
        marginLeft: 3
    },
    tableContents: {
        minHeight: '200px',
        maxHeight: 'calc(100vh - 250px)',
        overflowY: 'auto',
        overflowX: 'auto',
        '& .MuiTableCell-stickyHeader': {
            backgroundColor: theme.palette.background.default
        },
        borderRadius: theme.shape.borderRadius
    },
    tableFooter: {
        flex: 1
    },
    tableSettings: {
        position: 'relative',
        marginTop: -40,
        marginRight: 10,
        float: 'right',
        maxWidth: 50
    },
    tablePaginationLabel: {
        fontWeight: 'bold',
        alignContent: 'center'
    },
    viewColumnsFormControl: {
        padding: 10
    },
    messageBox: {
        padding: 0
    }
});

const LOCAL_STORAGE_METADATA_TABLE_ROWS_NUM_KEY = 'FAIRSPACE_METADATA_TABLE_ROWS_NUM';
const SESSION_STORAGE_VISIBLE_COLUMNS_KEY_PREFIX = 'FAIRSPACE_METADATA_VISIBLE_COLUMNS';

export const MetadataViewTableContainer = (props: MetadataViewTableContainerProperties) => {
    const {view, viewCountDisplayLimit, filters, columns, idColumn, hasInactiveFilters, locationContext, classes} =
        props;
    const {textFiltersObject, setTextFiltersObject} = props;

    const {isFeatureEnabled} = useContext(FeaturesContext);
    const exportToAnalysisEnabled = isFeatureEnabled('ExtraStorage');
    const [exportToAnalysisLoading, setExportToAnalysisLoading] = useState(false);
    const [currentSelectionExported, setCurrentSelectionExported] = useState(false);
    const theme = useTheme();

    const [page, setPage] = useState(0);
    const [visibleColumnNames, setVisibleColumnNames] = useStateWithLocalStorage(
        `${SESSION_STORAGE_VISIBLE_COLUMNS_KEY_PREFIX}_${view.toUpperCase()}`,
        columns.map(c => c.name)
    );
    const [rowsPerPage, setRowsPerPage] = useStateWithLocalStorage(LOCAL_STORAGE_METADATA_TABLE_ROWS_NUM_KEY, 10);
    const [anchorEl, setAnchorEl] = useState(null);

    const columnSelectorOpen = Boolean(anchorEl);
    const history = useHistory();

    const {data, count, error, countError, loading, loadingCount, refreshDataOnly} = useViewData(
        view,
        filters,
        textFiltersObject,
        locationContext,
        rowsPerPage
    );
    const viewCountDisplayLimitReached = viewCountDisplayLimit !== undefined && count.count === viewCountDisplayLimit;
    const [rowCheckboxes, setRowCheckboxes] = React.useState({});

    const resetRowCheckboxes = () => {
        setRowCheckboxes(() => ({}));
    };

    const setRowCheckboxState = (id: string, checked: boolean) => {
        if (rowCheckboxes) {
            setRowCheckboxes(oldState => {
                const newState = {...oldState};
                newState[id] = checked;
                return newState;
            });
        }
    };

    const handleChangePage = (e, p) => {
        setPage(p);
        refreshDataOnly(p, rowsPerPage);
    };

    const handleChangeRowsPerPage = e => {
        setRowsPerPage(e.target.value);
        setPage(0);
        refreshDataOnly(0, e.target.value);
    };

    const handleVisibleColumnsChange = event => {
        if (event.target.checked) {
            setVisibleColumnNames([...visibleColumnNames, event.target.name]);
        } else if (event.target.name !== idColumn.name) {
            setVisibleColumnNames([...visibleColumnNames.filter(cs => cs !== event.target.name)]);
        }
    };

    const handleColumnSelectorButtonClick = event => {
        setAnchorEl(event.currentTarget);
    };

    const handleColumnSelectorClose = () => {
        setAnchorEl(null);
    };

    const getCsvHeader = () => {
        let header = 'id';

        if (data && data.rows) {
            header += ';';
            header += visibleColumnNames.join(';');
        }

        return header;
    };

    // each row contains attributes with values. Each value is a dictionary with 'label' and 'value'
    const getCsvValuesForAttribute = (row, attribute) => {
        if (row[attribute] === undefined) {
            return '-';
        }
        return Object.values(row[attribute])
            .map(value => ((value && value.label) ?? '-').replaceAll(';', '.,'))
            .join(',');
    };

    const getCsvValues = () => {
        let values = '';
        if (Object.keys(rowCheckboxes).length > 0) {
            data.rows.forEach(row => {
                const rowKey = row[idColumn.name][0].value;
                if (Object.keys(rowCheckboxes).includes(rowKey) && rowCheckboxes[rowKey]) {
                    values += '\n' + rowKey;
                    visibleColumnNames.forEach(attribute => {
                        values += ';' + getCsvValuesForAttribute(row, attribute);
                    });
                }
            });
        }
        return values;
    };

    const createCsvBlob = (): Blob => {
        let csvFile = getCsvHeader();
        csvFile += getCsvValues();
        return new Blob([csvFile], {type: 'text/csv;charset=utf-8;'});
    };

    const exportTable = () => {
        const blob = createCsvBlob();
        const fileName = 'fairspace_export.csv';
        const link = document.createElement('a');
        if (link.download !== undefined) {
            // feature detection
            // Browsers that support HTML5 download attribute
            const url = URL.createObjectURL(blob);
            link.setAttribute('href', url);
            link.setAttribute('download', fileName);
            link.style = 'visibility:hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }
    };

    const saveTableExtraStorage = () => {
        setExportToAnalysisLoading(true);
        let csvFile = getCsvHeader();
        csvFile += getCsvValues();
        const fileName = `fairspace_export_${Date.now()}.csv`;
        ExtraLocalStorage.deleteAllInDirectory(ANALYSIS_EXPORT_SUBPATH)
            .then(() => ExtraLocalStorage.upload(csvFile, fileName, ANALYSIS_EXPORT_SUBPATH, true))
            .then(() => setCurrentSelectionExported(true))
            .catch((err: Error) => ErrorDialog.showError('Could not export the selection to analysis', err.message))
            .finally(() => setExportToAnalysisLoading(false));
    };

    const renderMessages = () => (
        <div className={classes.messageBox}>
            {count.timeout && (
                <MessageDisplay
                    color="warning"
                    messageColor={theme.palette.primary.main}
                    small
                    message="The count request timed out."
                />
            )}
            {hasInactiveFilters && (
                <MessageDisplay
                    color="primary"
                    isError={false}
                    small
                    message="Apply filters to see data matching your current selection."
                    messageColor={theme.palette.primary.main}
                />
            )}
        </div>
    );

    const renderColumnSelector = () => (
        <Popover
            open={columnSelectorOpen}
            onClose={handleColumnSelectorClose}
            anchorEl={anchorEl}
            anchorOrigin={{vertical: 'center', horizontal: 'left'}}
            transformOrigin={{vertical: 'top', horizontal: 'right'}}
        >
            <FormControl className={classes.viewColumnsFormControl}>
                <Typography variant="caption">Show/hide columns</Typography>
                <FormGroup>
                    {columns.map(column => (
                        <FormControlLabel
                            key={column.name}
                            control={
                                <Checkbox
                                    checked={visibleColumnNames.includes(column.name)}
                                    disabled={column.name === idColumn.name}
                                    onChange={handleVisibleColumnsChange}
                                    name={column.name}
                                    icon={<CheckBoxOutlineBlankIcon fontSize="small" />}
                                    checkedIcon={<CheckBoxIcon fontSize="small" />}
                                />
                            }
                            label={column.title}
                        />
                    ))}
                </FormGroup>
            </FormControl>
        </Popover>
    );

    const renderTableSettings = () => (
        <div className={classes.tableSettings}>
            <IconButton
                aria-label="Show/hide columns"
                title="Show/hide columns"
                onClick={handleColumnSelectorButtonClick}
                size="medium"
            >
                <ViewColumn color="primary" />
            </IconButton>
            {renderColumnSelector()}
        </div>
    );

    const getTotalCountString = (totalCount: number, to: number) => {
        if (totalCount === undefined) return '...';
        if (totalCount === -1) return `more than ${to}`;
        if (viewCountDisplayLimitReached) return `more than ${viewCountDisplayLimit - 1}`;
        return totalCount;
    };

    const renderTablePaginationLabel = ({from, to, countIsLoading, countHasError}) => (
        <span>
            <Typography variant="body2" component="span" display="inline">
                {from}-{data?.rows ? Number(from) + data.rows.length - 1 : '...'} of{' '}
            </Typography>
            <Typography variant="body2" component="span" display="inline" className={classes.tablePaginationLabel}>
                {getTotalCountString(count?.count, to)}
                {countIsLoading && <CircularProgress size={14} style={{marginLeft: 3}} />}
                {(countHasError || count?.timeout) && (
                    <Tooltip title="Error occured when counting results">
                        <ErrorOutline size="small" className={classes.countErrorIcon} color="warning" />
                    </Tooltip>
                )}
            </Typography>
        </span>
    );

    const renderMetadataViewTable = () => {
        if (error && error.message) {
            return <MessageDisplay message={error.message} />;
        }

        if (count.count === 0 && !data.timeout) {
            return <MessageDisplay message="No results found." />;
        }
        if (data && data.timeout) {
            return <MessageDisplay isError message="The data request timed out." />;
        }
        return (
            <MetadataViewTable
                {...props}
                visibleColumnNames={visibleColumnNames}
                idColumn={idColumn}
                data={data}
                loading={!data || loading}
                history={history}
                textFiltersObject={textFiltersObject}
                setTextFiltersObject={setTextFiltersObject}
                checkboxes={rowCheckboxes}
                setCheckboxState={setRowCheckboxState}
            />
        );
    };

    useEffect(() => {
        setPage(0);
    }, [filters]);

    useDeepCompareEffectNoCheck(() => {
        resetRowCheckboxes();
    }, [data]);

    useDeepCompareEffect(() => {
        if (rowCheckboxes && Object.keys(rowCheckboxes).length > 0 && Object.values(rowCheckboxes).includes(true)) {
            setCurrentSelectionExported(false);
        }
    }, [rowCheckboxes]);

    const checkedCount = Object.values(rowCheckboxes)
        ? Object.values(rowCheckboxes).reduce((sum, item) => (item === true ? sum + 1 : sum), 0)
        : 0;

    return (
        <Box border="1px solid" borderColor={theme.palette.primary.dark} borderRadius={5} sx={{boxShadow: 3}}>
            {renderTableSettings()}
            <LoadingOverlayWrapper loading={!data || loading}>
                <MetadataViewActiveTextFilters
                    textFiltersObject={textFiltersObject}
                    setTextFiltersObject={setTextFiltersObject}
                    columns={columns}
                />
                <TableContainer className={classes.tableContents}>
                    {renderMessages()}
                    {renderMetadataViewTable()}
                </TableContainer>
                <div className={classes.footerButtonDiv}>
                    <div className={classes.footerCountDiv}>
                        <Typography variant="body2" component="span" display="inline">
                            Selected rows: {checkedCount}
                        </Typography>
                    </div>
                    <Tooltip title="Download as csv" className={classes.exportButton}>
                        <span>
                            <IconButton
                                aria-label="Download as csv"
                                color="primary"
                                disabled={checkedCount === 0}
                                onClick={exportTable}
                            >
                                <GetAppIcon fontSize="small" />
                            </IconButton>
                        </span>
                    </Tooltip>
                    {exportToAnalysisEnabled && (
                        <ProgressButton active={exportToAnalysisLoading}>
                            <Tooltip
                                title="Export to Jupiter Analysis"
                                className={classes.exportButton}
                                component="span"
                            >
                                <span>
                                    <IconButton
                                        aria-label="Export to Jupiter Analysis"
                                        color="primary"
                                        disabled={checkedCount === 0 || currentSelectionExported}
                                        onClick={saveTableExtraStorage}
                                    >
                                        {currentSelectionExported ? (
                                            <Check fontSize="small" />
                                        ) : (
                                            <Addchart fontSize="small" />
                                        )}
                                    </IconButton>
                                </span>
                            </Tooltip>
                        </ProgressButton>
                    )}
                    <TablePagination
                        rowsPerPageOptions={[5, 10, 25, 100]}
                        component="div"
                        count={count && isNonEmptyValue(count.count) ? count.count : -1}
                        rowsPerPage={rowsPerPage}
                        page={page}
                        onPageChange={handleChangePage}
                        onRowsPerPageChange={handleChangeRowsPerPage}
                        className={classes.tableFooter}
                        labelDisplayedRows={d =>
                            renderTablePaginationLabel({...d, countIsLoading: loadingCount, countHasError: countError})
                        }
                        ActionsComponent={p =>
                            TablePaginationActions({
                                ...p,
                                countDisplayLimitReached: viewCountDisplayLimitReached,
                                hasNextFlag: data?.hasNext
                            })
                        }
                    />
                </div>
            </LoadingOverlayWrapper>
        </Box>
    );
};

export default withStyles(styles)(MetadataViewTableContainer);
