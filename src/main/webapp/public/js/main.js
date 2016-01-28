(function($) {
    'use strict';

    function Fixedheader(table, scrollbarWidth) {
        this.table = table;
        this.scrollbarWidth = scrollbarWidth;
        this.init();
        return this;
    }

    Fixedheader.prototype.init = function() {
        this.wrapper = $('<div class="fixed-table-wrapper"></div>');
        this.header = this.table.find('thead');
        this.fixedTableWrapper = $('<div></div>');
        this.fixedHeader = this.header.clone();
        this.fixedTable = $('<table></table>').addClass("table table-bordered table-striped fixed-header");
        this.fixedCells = this.fixedHeader.find('th');
        this.wrapper.insertBefore(this.table);
        this.wrapper.append(this.table);
        this.fixedTableWrapper.append(this.fixedTable.append(this.fixedHeader));
        this.fixedTableWrapper.insertBefore(this.wrapper);
        this.updateSize();
        this.header.hide();
        if(this.table.attr("id")) {
            var me = this;
            var id = this.table.attr("id");
            this.wrapper.on('scroll', function() {
                me.storeScroll(id, this.scrollTop);
            });
            this.wrapper[0].scrollTop = this.getScroll(id);
        }
    };
    
    Fixedheader.prototype.storeScroll = function(id, pos) {
        window.localStorage.setItem(id, pos);
    };
    
    Fixedheader.prototype.getScroll = function(id) {
        var scrolltop = window.localStorage.getItem(id);
        if(scrolltop) {
            return scrolltop;
        }
        return 0;
    };

    Fixedheader.prototype.updateSize = function() {
        var fixedCells = this.fixedCells;
        var headerCells = this.header.find('th');
        this.wrapper.css("width", "auto");
        headerCells.css("width", "");
        var firstRow = this.table.find("tbody tr:first-of-type td");
        this.header.show();
        var brTable = this.table[0].getBoundingClientRect();
        var scrollbarWidth = this.scrollbarWidth;
        if(this.wrapper[0].scrollHeight <= this.wrapper[0].clientHeight) {
            scrollbarWidth = 0;
        }
        this.wrapper[0].style.width = brTable.width + 'px';
        this.fixedTableWrapper[0].style.width = brTable.width - scrollbarWidth + 'px';
        this.wrapper[0].style.marginRight = -scrollbarWidth + 'px';
        headerCells.each(function(idx) {
            var br = this.getBoundingClientRect();
            fixedCells[idx].style.width = br.width + 'px';
            firstRow[idx].style.width = br.width + 'px';
            this.style.width = br.width + 'px';
        });
        this.header.hide();
    };

    function getScrollBarWidth() {
        var $outer = $('<div>').css({visibility: 'hidden', width: 100, overflow: 'scroll'}).appendTo('body'),
            widthWithScroll = $('<div>').css({width: '100%'}).appendTo($outer).outerWidth();
        $outer.remove();
        return 100 - widthWithScroll;
    }

    $(document).ready(function() {
        // Add global listener to .remove-item links to add confirmation message
        $('.remove-item').on('click', function(e) {
            if(!confirm('Weet u zeker dat u dit item wilt verwijderen?')) {
                e.stopPropagation();
                e.preventDefault();
            }
        });

        /**
         * Add fixed headers
         */
        var fixedHeaders = [];
        var scrollbarWidth = getScrollBarWidth();
        $('.table-fixed-header').each(function() {
            fixedHeaders.push(new Fixedheader($(this), scrollbarWidth));
        });
        $(window).on('resize', function() {
            for(var i = 0; i < fixedHeaders.length; i++) {
                fixedHeaders[i].updateSize.apply(fixedHeaders[i]);
            }
        });
    });
})(jQuery);