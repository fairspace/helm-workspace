const styles = theme => ({
    head: {
        backgroundColor: "#FFF",
        position: "sticky",
        top: 0,
        zIndex: 10,
    },
    tableRow: {
        '&:hover': {
            backgroundColor: theme.palette.primary[50],
            cursor: 'pointer'
        }
    },
    tableRowSelected: {
        '&&': {
            backgroundColor: theme.palette.primary[100]
        }
    }
});

export default styles;
