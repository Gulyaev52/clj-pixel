import React, { useState, Fragment } from "react";
import tinycolor2 from "tinycolor2";
import Saturation from "@uiw/react-color-saturation";
import Alpha from "@uiw/react-color-alpha";
import EditableInput from "@uiw/react-color-editable-input";
import RGBA from "@uiw/react-color-editable-input-rgba";
import Hue from "@uiw/react-color-hue";
import {
  validHex,
  hsvaToHex,
  hsvaToRgbaString,
  hexToHsva,
  color as handleColor,
  getContrastingColor
} from "@uiw/color-convert";
import Swatch from "@uiw/react-color-swatch";
import { useEffect } from "react";
import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";

const Bar = (props) =>
  /*#__PURE__*/ _jsx("div", {
    style: {
      boxShadow: "rgb(0 0 0 / 60%) 0px 0px 2px",
      width: 4,
      top: 1,
      bottom: 1,
      left: props.left,
      borderRadius: 1,
      position: "absolute",
      backgroundColor: "#fff"
    }
  });
const Sketch = /*#__PURE__*/ React.forwardRef((props, ref) => {
  const {
    prefixCls = "w-color-sketch",
    className,
    onChange,
    width = 218,
    presetColors,
    color,
    editableDisable = true,
    disableAlpha = false,
    actions,
    style,
    ...other
  } = props;
  const [hsva, setHsva] = useState({
    h: 209,
    s: 36,
    v: 90,
    a: 1
  });
  useEffect(() => {
    setHsva(hexToHsva(tinycolor2(color).toHexString()));
  }, [color]);
  const handleChange = (hsv) => {
    setHsva(hsv);
    onChange && onChange(handleColor(hsv));
  };
  const handleHex = (value, evn) => {
    if (
      typeof value === "string" &&
      validHex(value) &&
      /(3|6)/.test(String(value.length))
    ) {
      handleChange(hexToHsva(value));
    }
  };
  const handleAlphaChange = (newAlpha) =>
    handleChange({
      ...hsva,
      ...{
        a: newAlpha.a
      }
    });
  const handleSaturationChange = (newColor) =>
    handleChange({
      ...hsva,
      ...newColor,
      a: hsva.a
    });
  const styleMain = {
    "--sketch-background": "rgb(255, 255, 255)",
    "--sketch-box-shadow":
      "rgb(0 0 0 / 15%) 0px 0px 0px 1px, rgb(0 0 0 / 15%) 0px 8px 16px",
    "--sketch-swatch-box-shadow": "rgb(0 0 0 / 15%) 0px 0px 0px 1px inset",
    "--sketch-alpha-box-shadow":
      "rgb(0 0 0 / 15%) 0px 0px 0px 1px inset, rgb(0 0 0 / 25%) 0px 0px 4px inset",
    "--sketch-swatch-border-top": "1px solid rgb(238, 238, 238)",
    background: "var(--sketch-background)",
    borderRadius: 4,
    boxShadow: "var(--sketch-box-shadow)",
    width,
    ...style
  };
  const styleAlpha = {
    borderRadius: 2,
    background: hsvaToRgbaString(hsva),
    boxShadow: "var(--sketch-alpha-box-shadow)"
  };
  const styleSwatch = {
    borderTop: "var(--sketch-swatch-border-top)",
    paddingTop: 10,
    paddingLeft: 10
  };
  const styleSwatchRect = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 10,
    marginBottom: 10,
    borderRadius: 3,
    boxShadow: "var(--sketch-swatch-box-shadow)"
  };
  console.log("preset", presetColors);
  return /*#__PURE__*/ _jsxs("div", {
    ...other,
    className: `${prefixCls} ${className || ""}`,
    ref: ref,
    style: styleMain,
    children: [
      /*#__PURE__*/ _jsxs("div", {
        style: {
          padding: "10px 10px 8px"
        },
        children: [
          /*#__PURE__*/ _jsx(Saturation, {
            hsva: hsva,
            style: {
              width: "auto",
              height: 150
            },
            onChange: handleSaturationChange
          }),
          /*#__PURE__*/ _jsxs("div", {
            style: {
              display: "flex",
              marginTop: 4
            },
            children: [
              /*#__PURE__*/ _jsxs("div", {
                style: {
                  flex: 1
                },
                children: [
                  /*#__PURE__*/ _jsx(Hue, {
                    width: "auto",
                    height: 10,
                    hue: hsva.h,
                    pointer: Bar,
                    innerProps: {
                      style: {
                        marginLeft: 1,
                        marginRight: 5
                      }
                    },
                    onChange: (newHue) =>
                      handleChange({
                        ...hsva,
                        ...newHue
                      })
                  })
                ]
              }),
            ]
          })
        ]
      }),
      editableDisable &&
        /*#__PURE__*/ _jsxs("div", {
          style: {
            display: "flex",
            margin: "0 10px 3px 10px"
          },
          children: [
            /*#__PURE__*/ _jsx(EditableInput, {
              label: "Hex",
              value: hsvaToHex(hsva).replace(/^#/, "").toLocaleUpperCase(),
              onChange: (evn, val) => handleHex(val, evn),
              style: {
                minWidth: 58
              }
            }),
            /*#__PURE__*/ _jsx(RGBA, {
              hsva: hsva,
              style: {
                marginLeft: 6
              },
              aProps: !disableAlpha ? {} : false,
              onChange: (result) => handleChange(result.hsva)
            })
          ]
        }),
        _jsx(Alpha.default, {
          width: "90%",
          height: 24,
          hsva: hsva,
          radius: 2,
          style: {
            margin: "0px 10px 3px"
          },
          bgProps: {
            style: {
              background: "transparent"
            }
          },
          innerProps: {
            style: styleAlpha
          },
          pointer: () => /*#__PURE__*/ _jsx(Fragment, {})
        }),
      presetColors &&
        presetColors.length > 0 &&
        /*#__PURE__*/ _jsx(Swatch, {
          style: styleSwatch,
          colors: presetColors,
          color: hsvaToHex(hsva),
          rectProps: {
            children: _jsx(CheckedSwatchColorPoint, {}),
            style: styleSwatchRect
          },
          onChange: (hsvColor) => handleChange(hsvColor),
        }),
      _jsx("div", {
        style: {
          display: 'flex',
          justifyContent: "flex-end",
          padding: "5px 10px",
          borderTop: "var(--sketch-swatch-border-top)"
        },
        children: actions
      })
    ]
  });
});
Sketch.displayName = "Sketch";

const CheckedSwatchColorPoint = (props) => {
  if (!props.checked) return null;
  return _jsx(
    "div",
    {style: {
      height: 5,
      width: 5,
      borderRadius: '50%',
      backgroundColor: getContrastingColor(props.color),
    }}
  );
}

export default Sketch;
