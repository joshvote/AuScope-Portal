/**
 * An abstract base class to be extended.
 *
 * Represents a grid panel for containing layers
 * that haven't yet been added to the map. Each row
 * will be grouped under a heading, contain links
 * to underlying data sources and have a spatial location
 * that can be viewed by the end user.
 *
 * This class is expected to be extended for usage within
 * the 'Registered Layers', 'Known Layers' and 'Custom Layers'
 * panels in the portal. Support for KnownLayers/CSWRecords and
 * other row types will be injected by implementing the abstract
 * functions of this class
 *
 */

Ext.define('portal.widgets.panel.BaseActiveRecordPanel', {
    extend : 'portal.widgets.panel.CommonBaseRecordPanel',
    alias: 'widget.baseactiverecordpanel',

    visibleIcon : 'img/eye.png',
    notVisibleIcon : 'img/eye_off.png',

    constructor : function(cfg) {
        var me = this;
        this.store = cfg.store;

        Ext.apply(cfg, {
            cls : 'auscope-dark-grid',
            allowReordering: true,
            titleField: 'name',
            titleIndex: 1,
            tools: [{
                field: 'name',
                stopEvent: false,
                tipRenderer: function(value, record, tip) {
                    return 'Click to adjust transparency and/or query filters';
                },
                iconRenderer: me._playRenderer
            },{
                field: 'info',
                stopEvent: true,
                tipRenderer: function(value, record, tip) {
                    return 'Show layer information';
                },
                iconRenderer: function(value, record) {
                    return 'portal-core/img/information.png'
                },
                clickHandler: me._serviceInformationClickHandler
            },{
                field: 'legend',
                stopEvent: true,
                tipRenderer: function(value, record, tip) {
                    return 'Show layer legend';
                },
                iconRenderer: function(value, record) {
                    return 'portal-core/img/key.png'
                },
                clickHandler: function(value, record) {
                    me._getLegendAction(record).execute();
                }
            },{
                field: 'visible',
                stopEvent: true,
                tipRenderer: function(value, record, tip) {
                    var tip = 'Toggle layer visibility ';
                    if(record.visible){
                        tip+='off';
                    }else{
                        tip+='on';
                    }
                    return tip;
                },
                iconRenderer: function(value, record) {
                    if(record.visible){
                        return me.visibleIcon;
                    }else{
                        return me.notVisibleIcon;
                    }
                },
                clickHandler: function(value, record) {
                    me._setVisibilityAction(record).execute();
                }
            },{
                field: 'remove',
                stopEvent: true,
                tipRenderer: function(value, record, tip) {
                    return 'Remove layer from map';
                },
                iconRenderer: function(value, record) {
                    return 'portal-core/img/cross.png';
                },
                clickHandler: function(value, record) {
                    ActiveLayerManager.removeLayer(record);
                }
            }],
            childPanelGenerator: function(record) {
                //For every filter panel we generate, also generate a portal.layer.Layer and
                //attach it to the CSWRecord/KnownLayer
                var newLayer = null;
                if (record instanceof portal.csw.CSWRecord) {
                    newLayer = cfg.layerFactory.generateLayerFromCSWRecord(record);
                } else {
                    newLayer = cfg.layerFactory.generateLayerFromKnownLayer(record);
                }
                record.set('layer', newLayer);
                return me._getInlineLayerPanel(newLayer.get('filterForm'));
            }
        });

        this.callParent(arguments);
    },

    // Column Function
    _getLegendAction : function(layer){
        var legend = layer.get('renderer').getLegend();
        var text = 'Get Legend';

        var getLegendAction = new Ext.Action({
            text : text,
            icon : legend.iconUrl,
            //icon : null,
            iconCls : 'portal-ux-menu-icon-size',
            itemId : 'LegendAction',

            handler : function(){
                // this will be resized dynamically as legend content is added
                var legendCallback = function(legend, resources, filterer, success, form, layer){
                    if (success && form) {
                        // allow more than one legend popup but only one per layer
                        var popupId = 'legendPopup_' + layer.get('id');
                        var popupWindow = Ext.get(popupId);
                        if (!popupWindow) {
                            popupWindow = Ext.create('Ext.window.Window', {
                                id          : 'legendPopup',
                                title       : 'Legend: '+ layer.get('name'),
                                layout      : 'vbox',
                                maxHeight   : Ext.get('center_region-map').getHeight(),
                                autoScroll  : true,
                                items: form,
                                listeners: {
                                    show: function() {
                                        var container = Ext.get('center_region-map');
                                        this.setPosition(container.getX()-1, container.getY()-1);
                                    }
                                },
                            });
                            popupWindow.show();
                        }
                        return Ext.getCmp(popupWindow.id).focus();
                    }
                };

                var onlineResources = layer.getAllOnlineResources();
                var filterer = layer.get('filterer');
                var renderer = layer.get('renderer');
                var legend = renderer.getLegend(onlineResources, filterer);

                //VT: this style is just for the legend therefore no filter is required.
                var styleUrl = layer.get('renderer').parentLayer.get('source').get('proxyStyleUrl');

                //VT: if a layer has style, the style should take priority as the default GetLegend source else use default
                if(styleUrl && styleUrl.length > 0){

                    Ext.Ajax.request({
                        url: styleUrl,
                        timeout : 180000,
                        scope : this,
                        success:function(response,opts){
                            legend.getLegendComponent(onlineResources, filterer,response.responseText, true, Ext.bind(legendCallback, this, [layer], true), null, true);
                        },
                        failure: function(response, opts) {
                            legend.getLegendComponent(onlineResources, filterer,"", true, Ext.bind(legendCallback, this, [layer], true));
                        }
                    });

                }else{
                    legend.getLegendComponent(onlineResources, filterer,"", true, Ext.bind(legendCallback, this, [layer], true), layer.get('source').get('staticLegendUrl'), true);
                }

            }
        });

        return getLegendAction;
    },

    // Column Function
    _setVisibilityAction : function(layer){
//        var me = this;
        var visibleLayerAction = new Ext.Action({
            text : 'Toggle Layer Visibility OFF',
            iconCls : 'visible_eye',
            handler : function(){
//                var layer = me.filterForm.layer;
                layer.setLayerVisibility(!layer.visible);
            }
        });

        return visibleLayerAction;
    },

    /**
     * Column definition function to draw the panel when a row is clicked upon.  Here is a common one to draw the WMS/WFS filter with Opacity, drop-downs etc..
     * Override
     */
    _getInlineLayerPanel : function(filterForm, parentElId){
        var me = this;
        var panel =Ext.create('portal.widgets.panel.FilterPanel', {
            wantAddLayerButton : false,
            wantUpdateLayerButton : true,
            wantOptionsButton : false,
            menuFactory : this.menuFactory,
            filterForm  : filterForm,
            detachOnRemove : false,
            map         : this.map,
            renderTo    : parentElId,
            layerStore  : me.store
        });

        return panel
    },

    _playRenderer : function () {
        return 'portal-core/img/play_blue.png';
    }
});
