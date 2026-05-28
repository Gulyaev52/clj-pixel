import React, { useState, useCallback, Fragment, CSSProperties } from 'react';
import Saturation from '@uiw/react-color-saturation';
import Alpha from '@uiw/react-color-alpha';
import EditableInput from '@uiw/react-color-editable-input';
import RGBA from '@uiw/react-color-editable-input-rgba';
import Hue from '@uiw/react-color-hue';
import { validHex, HsvaColor, hsvaToHex, hsvaToRgbaString, hexToHsva, color as handleColor, rgbaStringToHsva, ColorResult, getContrastingColor } from '@uiw/color-convert';
import { useEffect } from 'react';
import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import Swatch from "./swatch";
import tinycolor from 'tinycolor2';

const PRESET_COLORS = ['#D0021B', '#F5A623', '#f8e61b', '#8B572A', '#7ED321', '#417505', '#BD10E0', '#9013FE', '#4A90E2', '#50E3C2', '#B8E986', '#000000', '#4A4A4A', '#9B9B9B', '#FFFFFF'];
const Bar = props => /*#__PURE__*/_jsx("div", {
  style: {
    boxShadow: 'rgb(0 0 0 / 60%) 0px 0px 2px',
    width: 4,
    top: 1,
    bottom: 1,
    left: props.left,
    borderRadius: 1,
    position: 'absolute',
    backgroundColor: '#fff'
  }
});
const Sketch = /*#__PURE__*/React.forwardRef((props, ref) => {
  const {
    prefixCls = 'w-color-sketch',
    className,
    onChange,
    width = 218,
    presetColors = PRESET_COLORS,
    color,
    editableDisable = true,
    disableAlpha = false,
    style,
    actions,
    ...other
  } = props;
  const [hsva, setHsva] = useState({
    h: 209,
    s: 36,
    v: 90,
    a: 1
  });
  useEffect(() => {
    if (typeof color === 'string') {
      setHsva(rgbaStringToHsva(color));
    }
    if (typeof color === 'object') {
      setHsva(color);
    }
  }, []);
  const handleChange = hsv => {
    setHsva(hsv);
    onChange && onChange(handleColor(hsv));
  };
  const handleHex = (value, evn) => {
    if (typeof value === 'string' && validHex(value) && /(3|6)/.test(String(value.length))) {
      handleChange(hexToHsva(value));
    }
  };
  const handleAlphaChange = newAlpha => handleChange({
    ...hsva,
    ...{
      a: newAlpha.a
    }
  });
  const handleSaturationChange = newColor => {
    return handleChange({
      ...hsva,
      ...newColor,
      a: disableAlpha ? 1 : hsva.a
    });
  };
  const styleMain = {
    '--sketch-background': 'rgb(255, 255, 255)',
    '--sketch-box-shadow': 'rgb(0 0 0 / 15%) 0px 0px 0px 1px, rgb(0 0 0 / 15%) 0px 8px 16px',
    '--sketch-swatch-box-shadow': 'rgb(0 0 0 / 15%) 0px 0px 0px 1px inset',
    '--sketch-alpha-box-shadow': 'rgb(0 0 0 / 15%) 0px 0px 0px 1px inset, rgb(0 0 0 / 25%) 0px 0px 4px inset',
    '--sketch-swatch-border-top': '1px solid rgb(238, 238, 238)',
    background: 'var(--sketch-background)',
    borderRadius: 4,
    boxShadow: 'var(--sketch-box-shadow)',
    width,
    ...style
  };
  const styleAlpha = {
    borderRadius: 2,
    background: hsvaToRgbaString(hsva),
    boxShadow: 'var(--sketch-alpha-box-shadow)'
  };
  const styleSwatch = {
    borderTop: 'var(--sketch-swatch-border-top)',
    paddingTop: 10,
    paddingLeft: 10
  };
  const styleSwatchRect = {
    marginRight: 10,
    marginBottom: 10,
    borderRadius: 3,
    boxShadow: 'var(--sketch-swatch-box-shadow)'
  };
  return /*#__PURE__*/_jsxs("div", {
    ...other,
    className: `${prefixCls} ${className || ''}`,
    ref: ref,
    style: styleMain,
    children: [/*#__PURE__*/_jsxs("div", {
      style: {
        padding: '10px 10px 8px'
      },
      children: [/*#__PURE__*/_jsx(Saturation, {
        hsva: hsva,
        style: {
          width: 'auto',
          height: 150
        },
        onChange: handleSaturationChange
      }), /*#__PURE__*/_jsxs("div", {
        style: {
          display: 'flex',
          marginTop: 4
        },
        children: [/*#__PURE__*/_jsxs("div", {
          style: {
            flex: 1
          },
          children: [/*#__PURE__*/_jsx(Hue, {
            width: "auto",
            height: 24,
            hue: hsva.h,
            pointer: Bar,
            innerProps: {
              style: {
                marginLeft: 1,
                marginRight: 5
              }
            },
            onChange: newHue => {
              console.log("hue");
              return handleChange({
                ...hsva,
                ...newHue
              });
            }
          }), !disableAlpha && /*#__PURE__*/_jsx(Alpha.default, {
            width: "auto",
            height: 10,
            hsva: hsva,
            pointer: Bar,
            style: {
              marginTop: 4
            },
            innerProps: {
              style: {
                marginLeft: 1,
                marginRight: 5
              }
            },
            onChange: handleAlphaChange
          })]
        }),  /*#__PURE__*/_jsx(Alpha.default, {
          width: 24,
          height: 24,
          hsva: hsva,
          radius: 2,
          style: {
            marginLeft: 4
          },
          bgProps: {
            style: {
              background: 'transparent'
            }
          },
          innerProps: {
            style: styleAlpha
          },
          pointer: () => /*#__PURE__*/_jsx(Fragment, {})
        })]
      })]
    }), editableDisable && /*#__PURE__*/_jsxs("div", {
      style: {
        display: 'flex',
        margin: '0 10px 3px 10px'
      },
      children: [/*#__PURE__*/_jsx(EditableInput, {
        label: "Hex",
        "data-testid": "color-picker-hex-input",
        value: hsvaToHex(hsva).replace(/^#/, '').toLocaleUpperCase(),
        onChange: (evn, val) => handleHex(val, evn),
        style: {
          minWidth: 58
        }
      }), /*#__PURE__*/_jsx(RGBA, {
        hsva: hsva,
        style: {
          marginLeft: 6
        },
        rProps: { 'data-testid': 'color-picker-r-input' },
        gProps: { 'data-testid': 'color-picker-g-input' },
        bProps: { 'data-testid': 'color-picker-b-input' },
        aProps: !disableAlpha ? {} : false,
        onChange: result => handleChange(result.hsva)
      })]
    }), presetColors && presetColors.length > 0 && /*#__PURE__*/_jsx(Swatch, {
      style: styleSwatch,
      colors: presetColors,
      color: hsvaToRgbaString(hsva),
      onChange: hsvColor => {
        return handleChange(rgbaStringToHsva(hsvColor));
      },
      rectProps: {
        style: styleSwatchRect
      },
      rectRender: (props) => {
        return (_jsxs("div", {
          title: props.title,
          onClick: props.onClick,
          style: {
            ...props.style,
            background: isTransparent(props.color) ? "url('data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAMAAABEpIrGAAAABlBMVEVMTExVVVUnhsEkAAAAHUlEQVR4AWOAAUYoQOePEAUj3v9oYDQ9gMBoegAAJFwCAbLaTIMAAAAASUVORK5CYII=')" : props.color
          },
          children: [_jsxs("div", {
            style: {
              display: "flex",
              alignItems: "center",
              height: "100%",
              justifyContent: "center"
            },
            children: [_jsx(CheckedSwatchColorPoint, props)]
          })]
        }));
      }
    }),
    _jsx("div", {
      style: {
        display: 'flex',
        justifyContent: "flex-end",
        padding: "5px 10px",
        borderTop: "var(--sketch-swatch-border-top)"
      },
      children: actions
    })]
  });
});

const CheckedSwatchColorPoint = (props) => {
  if (!props.checked) return null;
  return _jsx(
    "div",
    {style: {
      height: 5,
      width: 5,
      borderRadius: '50%',
      backgroundColor: isTransparent(props.color) ? "black" : getContrastingColor(rgbaStringToHsva(props.color)),
    }}
  );
}

const isTransparent = (color) => {
  const { r, g, b, a } = tinycolor(color).toRgb();
  return r === 0 && g === 0 && b === 0  && a === 0;
}; 

Sketch.displayName = 'Sketch';

export default Sketch;