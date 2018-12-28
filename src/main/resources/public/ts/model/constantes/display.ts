export  class DISPLAY {
    LIST:String  = 'list';
    COLUMN :String = 'column';

    isList = (mode) => {
        return mode == this.LIST;
    };
    isColumn=(mode) => {
        return mode == this.COLUMN;
    };
}

export var SCREENS = {
    TABLETTE: 800,
    SMALL_TABLETTE: 65,
    FAT_MOBILE: 550,
    SMALL_MOBILE : 420
};
